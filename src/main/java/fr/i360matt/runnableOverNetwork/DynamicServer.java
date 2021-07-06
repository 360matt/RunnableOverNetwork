package fr.i360matt.runnableOverNetwork;

import java.io.*;
import java.net.*;

public class DynamicServer implements Closeable,ConnectionConstants {

    private final ServerSocket server;
    private final String password;
    private boolean logErrors = true;
    private boolean isolated = false;
    private boolean allowRemoteClose = false;

    public DynamicServer (int port, final String password) throws IOException {
        this.server = new ServerSocket(port);
        this.password = password;
    }

    public DynamicServer (final ServerSocket server, final String password) throws IOException {
        this.server = server;
        this.password = password;
        while (!server.isClosed()) {
            Socket clientSock = server.accept();
            hasNewClient(clientSock);
        }
    }

    public void listen() throws IOException {
        while (!server.isClosed()) {
            Socket clientSock;
            try {
                clientSock = server.accept();
            } catch (IOException ioe) {
                if ("Socket closed".equals(ioe.getMessage()))
                    return; // Happen on close, this is not an error
                throw ioe;
            }
            clientSock.setTcpNoDelay(true);
            hasNewClient(clientSock);
        }
    }

    private void hasNewClient (final Socket clientSock)  {
        new Thread(() -> {
            try {
                final DataInputStream receiver = new DataInputStream(clientSock.getInputStream());
                final DataOutputStream sender = new DataOutputStream(clientSock.getOutputStream());
                if (!IOHelper.readString(receiver).equals(this.password)) {
                    sender.writeByte(1);
                    sender.flush();
                    clientSock.close();
                    return;
                }
                sender.writeByte(0);
                sender.flush();
                final DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(isolated);

                while (!this.server.isClosed() && !clientSock.isClosed()) {
                    int code = receiver.readInt();
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
                            int filesize = receiver.readInt(); // Send file size in separate msg
                            String className = IOHelper.readString(receiver);
                            byte[] buffer = new byte[filesize];
                            IOHelper.readLarge(receiver, buffer);

                            dynamicClassLoader.putClass(className, buffer);
                            if (code == ID_SEND_EXEC_DATA) {
                                new Thread(() -> execRunnable(dynamicClassLoader, className, false)).start();
                            }
                            break;
                        }
                        case ID_EXEC_DATA:
                        case ID_EXEC_CLASS:
                            String className = IOHelper.readString(receiver);
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
                        default:
                            throw new IOException("Invalid packed ID: " + code);
                    }
                }
            } catch (final Exception ignored) {
                try {
                    clientSock.close();
                } catch (IOException ignored1) { }
            }
        }).start();
    }


    private void execRunnable (final DynamicClassLoader classLoader, final String className,boolean forceNew) {
       try {
           final Object obj;
           if (forceNew) {
               // Load the class from the classloader by name....
               final  Class<?> loadedClass = classLoader.loadClass(className);
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
           }
       } catch (final Exception e) {
           if (logErrors) e.printStackTrace();
       }
    }

    public void setLogErrors(boolean logErrors) {
        this.logErrors = logErrors;
    }

    public void setIsolated(boolean isolated) {
        this.isolated = isolated;
    }

    public void setAllowRemoteClose(boolean allowRemoteClose) {
        this.allowRemoteClose = allowRemoteClose;
    }

    @Override
    public void close () throws IOException {
        this.server.close();
    }

    public boolean isClosed() {
        return server == null || server.isClosed();
    }
}
