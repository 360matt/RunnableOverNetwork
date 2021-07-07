package fr.i360matt.runnableOverNetwork;

import fr.i360matt.runnableOverNetwork.auth.AESPasswordAuth;
import fr.i360matt.runnableOverNetwork.auth.AbstractAuth;

import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

public class DynamicServer implements Closeable, ConnectionConstants {

    private final ServerSocket server;
    private final AbstractAuth auth;
    private boolean logErrors = true;
    private boolean isolated = false;
    private boolean verbose = false;
    private boolean allowRemoteClose = false;
    private boolean allowUnsafeSerialisation = false;

    public DynamicServer (final int port, final String password) throws IOException {
        this(new ServerSocket(port), new AESPasswordAuth(password));
    }

    public DynamicServer (final ServerSocket server, final String password) {
        this(server, new AESPasswordAuth(password));
    }

    public DynamicServer (final int port, final AbstractAuth auth) throws IOException {
        this(new ServerSocket(port), auth);
    }

    public DynamicServer (final ServerSocket server, final AbstractAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void listen () throws IOException {
        while (!server.isClosed()) {
            try {
                final Socket clientSock = server.accept();
                clientSock.setTcpNoDelay(true);
                if (this.verbose)
                    System.out.println("New client: " + clientSock.getInetAddress().getHostAddress());
                hasNewClient(clientSock);
            } catch (final IOException ioe) {
                if ("Socket closed".equals(ioe.getMessage()))
                    return; // Happen on close, this is not an error
                throw ioe;
            }
        }
    }

    private void hasNewClient (final Socket clientSock)  {
        new Thread(() -> {
            try {
                final DataInputStream authReceiver = new DataInputStream(clientSock.getInputStream());
                final DataOutputStream authSender = new DataOutputStream(clientSock.getOutputStream());
                final AbstractAuth.AuthParams authParams = new AbstractAuth.AuthParams(
                        IOHelper.readSmallString(authReceiver), authReceiver, authSender);
                authParams.allowRemoteClose = this.allowRemoteClose;
                authParams.allowUnsafeSerialisation = this.allowUnsafeSerialisation;
                this.auth.authenticateServer(clientSock, authParams);
                final DataInputStream receiver = authParams.inputStream;
                final DataOutputStream sender = authParams.outputStream;
                final int clientProtocol = receiver.readInt();
                final boolean unsafeSerial = authParams.allowUnsafeSerialisation;
                final String address = clientSock.getInetAddress().getHostAddress();
                final String username = authParams.username.isEmpty() ? address : authParams.username;
                final ObjectInputStream objectReceiver = unsafeSerial ? new ObjectInputStream(receiver) : null;
                final ObjectOutputStream objectSender = unsafeSerial ? new ObjectOutputStream(sender) : null;
                sender.writeInt(PROTOCOL_VER);
                int flags = 0;
                if (authParams.allowRemoteClose)
                    flags |= FLAG_ALLOW_REMOTE_CLOSE;
                if (unsafeSerial)
                    flags |= FLAG_ALLOW_UNSAFE_SERIALISATION;
                sender.writeInt(flags);
                sender.flush();
                final DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(isolated);
                final Set<Integer> blockedActions = authParams.blockedActions;
                int maxCode = Integer.MAX_VALUE;
                while (!this.server.isClosed() && !clientSock.isClosed()) {
                    final int code = receiver.readInt();
                    if (this.verbose) System.out.println("Received Code: "+ code);
                    if (code > maxCode || blockedActions.contains(code)) {
                        if (code != ID_KEEP_ALIVE) {
                            System.out.println("Security violation: User " + username +
                                    " from " + address + " executed " + code + " (A blocked instruction)");
                        }
                        clientSock.close();
                        return;
                    }
                    switch (code) {
                        case ID_KEEP_ALIVE:
                            break;
                        case ID_CLOSE_SERVER:
                            if (allowRemoteClose) {
                                sender.writeByte(0);
                                sender.flush();
                                server.close();
                            } else {
                                sender.writeByte(1);
                                sender.flush();
                            }
                            break;
                        case ID_SEND_EXEC_DATA:
                        case ID_SEND_DATA: {
                            final int filesize = receiver.readInt(); // Send file size in separate msg
                            final String className = IOHelper.readString(receiver);
                            final byte[] buffer = new byte[filesize];
                            IOHelper.readLarge(receiver, buffer);

                            dynamicClassLoader.putClass(className, buffer);
                            if (code == ID_SEND_EXEC_DATA) {
                                new Thread(() -> execRunnable(dynamicClassLoader, className, false)).start();
                            }
                            break;
                        }
                        case ID_EXEC_DATA:
                        case ID_EXEC_CLASS: {
                            final String className = IOHelper.readString(receiver);
                            if (code == ID_EXEC_DATA) {
                                if (!dynamicClassLoader.hasClass(className)) {
                                    sender.writeByte(1);
                                    sender.flush();
                                    break;
                                }
                                sender.writeByte(0);
                                sender.flush();
                            }
                            new Thread(() -> execRunnable(dynamicClassLoader,
                                    className, code == ID_EXEC_CLASS)).start();
                            break;
                        }
                        case ID_EXEC_DATA_BLOCKING: {
                            final String className = IOHelper.readString(receiver);
                            if (!dynamicClassLoader.hasClass(className)) {
                                sender.writeByte(1);
                                sender.flush();
                                break;
                            }
                            sender.writeByte(0);
                            sender.flush();
                            sender.writeInt(execRunnable(dynamicClassLoader, className, false));
                            sender.flush();
                            break;
                        }
                        case ID_EXEC_DATA_FUNC: {
                            final String className = IOHelper.readString(receiver);
                            if (!dynamicClassLoader.hasClass(className)) {
                                sender.writeByte(1);
                                sender.flush();
                                break;
                            }
                            sender.writeByte(0);
                            sender.flush();
                            final Object inParm = IOHelper.deserialize(receiver, objectReceiver, dynamicClassLoader);
                            new Thread(() -> execFunction(dynamicClassLoader, className, inParm)).start();
                            break;
                        }
                        case ID_EXEC_DATA_FUNC_BLOCKING: {
                            final String className = IOHelper.readString(receiver);
                            if (!dynamicClassLoader.hasClass(className)) {
                                sender.writeByte(1);
                                sender.flush();
                                break;
                            }
                            sender.writeByte(0);
                            sender.flush();
                            final Object inParm = IOHelper.deserialize(receiver, objectReceiver, dynamicClassLoader);
                            Object outParm = null;
                            try {
                                outParm = execFunction(dynamicClassLoader, className, inParm);
                            } catch (Throwable throwable) {
                                if (this.logErrors) throwable.printStackTrace();
                            }
                            IOHelper.serialize(sender, objectSender, outParm, clientProtocol);
                            break;
                        }
                        case ID_SEC_BLOCK_ACTIONS: {
                            final int newMax = receiver.readInt();
                            final int[] blocks = IOHelper.readInts(receiver);
                            if (newMax != 0) maxCode = Math.min(maxCode, newMax);
                            for (int block:blocks) blockedActions.add(block);
                            break;
                        }
                        default:
                            throw new IOException("Invalid packed ID: " + code);
                    }
                }
            } catch (final Exception ignored) {
                try {
                    clientSock.close();
                } catch (final IOException ignored1) { }
            }
        }).start();
    }


    private int execRunnable (final DynamicClassLoader classLoader, final String className, final boolean forceNew) {
       try {
           final Object obj;
           if (forceNew) {
               // Load the class from the classloader by name....
               final Class<?> loadedClass = classLoader.loadClass(className);
               // Create a new instance...
               obj = loadedClass.newInstance();
           } else {
               // Load an instance...
               obj = classLoader.loadInstance(className);
           }
           // Santity check
           if (obj instanceof Runnable) {
               // Cast to the DoStuff interface
               final Runnable stuffToDo = (Runnable)obj;
               // Run it baby
               stuffToDo.run();
           } else if (obj instanceof IntSupplier) {
               // Cast to the DoStuff interface
               final IntSupplier stuffToDo = (IntSupplier)obj;
               // Run it baby
               return stuffToDo.getAsInt();
           } else if (obj instanceof Consumer<?>) {
               // Cast to the DoStuff interface
               final Consumer<?> stuffToDo = (Consumer<?>)obj;
               // Run it baby
               stuffToDo.accept(null);
           } else if (obj instanceof Function<?, ?>) {
               // Cast to the DoStuff interface
               final Function<?, ?> stuffToDo = (Function<?, ?>)obj;
               // Run it baby
               Object ret = stuffToDo.apply(null);
               // Hashcode of integer is itself
               return Objects.hashCode(ret);
           }
           return 0;
       } catch (final Exception e) {
           if (logErrors) e.printStackTrace();
           return e.hashCode();
       }
    }

    @SuppressWarnings("unchecked")
    private Object execFunction (final DynamicClassLoader classLoader, final String className, final Object input) {
        try {
            final Object obj;
            // Load an instance...
            obj = classLoader.loadInstance(className);
            // Santity check
            if (obj instanceof Consumer<?>) {
                // Cast to the DoStuff interface
                final Consumer<Object> stuffToDo = (Consumer<Object>)obj;
                // Run it baby
                stuffToDo.accept(input);
            }  else if (obj instanceof Function<?, ?>) {
                // Cast to the DoStuff interface
                final Function<Object, Object> stuffToDo = (Function<Object, Object>)obj;
                // Run it baby
                return stuffToDo.apply(input);
            } else if (obj instanceof Runnable) {
                // Cast to the DoStuff interface
                final Runnable stuffToDo = (Runnable)obj;
                // Run it baby
                stuffToDo.run();
            } else if (obj instanceof IntSupplier) {
                // Cast to the DoStuff interface
                final IntSupplier stuffToDo = (IntSupplier)obj;
                // Run it baby
                return stuffToDo.getAsInt();
            }
            return null;
        } catch (final Exception e) {
            if (logErrors) e.printStackTrace();
            return e.hashCode();
        }
    }

    public void setLogErrors (final boolean logErrors) {
        this.logErrors = logErrors;
    }

    public void setIsolated (final boolean isolated) {
        this.isolated = isolated;
    }

    public void setAllowRemoteClose (final boolean allowRemoteClose) {
        this.allowRemoteClose = allowRemoteClose;
    }

    public void setAllowUnsafeSerialisation(boolean allowUnsafeSerialisation) {
        this.allowUnsafeSerialisation = allowUnsafeSerialisation;
    }

    @Override
    public void close () throws IOException {
        this.server.close();
    }

    public boolean isClosed () {
        return server == null || server.isClosed();
    }
}
