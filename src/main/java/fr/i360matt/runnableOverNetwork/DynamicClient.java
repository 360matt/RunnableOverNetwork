package fr.i360matt.runnableOverNetwork;


import fr.i360matt.runnableOverNetwork.auth.AESPasswordAuth;
import fr.i360matt.runnableOverNetwork.auth.AbstractAuth;

import java.io.*;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

public class DynamicClient implements Closeable, ConnectionConstants {

    private final Object writeLock = new Object();
    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;
    private final ConcurrentHashMap<String, Remote> clRunnable;
    private final int remoteProtocol;
    private final int remoteFlags;
    private long lastWrite;

    private class Remote implements Runnable, IntSupplier, Consumer<Object>, Function<Object, Object> {
        private final String name;

        private Remote(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            try {
                execData(this.name);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int getAsInt() {
            try {
                return execDataBlocking(this.name);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void accept(Object o) {
            try {
                execFunc(this.name, o);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object apply(Object o) {
            try {
                return execFuncBlocking(this.name, o);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Deprecated
    public DynamicClient (final String host, final int port, final String password) throws IOException {
        this(host, port, "", password);
    }

    public DynamicClient (final String host, final int port,final String username, final String password) throws IOException {
        this(new Socket(host, port), username, password);
    }

    public DynamicClient (final Socket socket,final String username, final String password) throws IOException {
        this(socket, username, new AESPasswordAuth(password));
    }

    public DynamicClient (final Socket socket,final String username, final AbstractAuth auth) throws IOException {
        socket.setTcpNoDelay(true);
        this.socket = socket;
        AbstractAuth.AuthParams authParams = new AbstractAuth.AuthParams(username,
                new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()));
        IOHelper.writeSmallString(authParams.outputStream, authParams.username = username);
        try {
            auth.authenticateClient(socket, authParams);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        this.dos = authParams.outputStream;
        this.dis = authParams.inputStream;
        this.dos.writeInt(PROTOCOL_VER);
        this.dos.flush();
        this.remoteProtocol = this.dis.readInt();
        this.remoteFlags = this.dis.readInt();
        final boolean unsafeSerial = this.allowUnsafeSerialisation();
        this.oos = unsafeSerial ? new ObjectOutputStream(this.dos) : null;
        this.ois = unsafeSerial ? new ObjectInputStream(this.dis) : null;
        this.clRunnable = new ConcurrentHashMap<>();
        this.startKeepAlive();
    }

    private void startKeepAlive () {
        this.lastWrite = System.currentTimeMillis();
        new Thread() {
            @Override
            public void run () {
                try {
                    while (!this.isInterrupted() && !socket.isClosed()) {
                        final long current = System.currentTimeMillis();
                        if (lastWrite + 5000 < current) {
                            sendKeepAlive();
                        }
                        TimeUnit.MILLISECONDS.sleep(5000);
                    }
                } catch (final InterruptedException | IOException e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        }.start();
    }
    private void checkNotBoostrap (final Class<?> clazz) {
        if (clazz.getClassLoader() == null) {
            throw new RuntimeException("Not compatible with boostrap classes");
        }
    }

    public void execRunnable (final Class<? extends Runnable> clazz) {
        this.sendRunnable(clazz).run();
    }

    @SuppressWarnings("unchecked")
    public <T> Consumer<T> sendConsumer (final Class<? extends Consumer<T>> clazz) {
        this.checkNotBoostrap(clazz);
        return (Consumer<T>) this.sendRemote(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T, R> Function<T, R> sendFunction (final Class<? extends Function<T, R>> clazz) {
        this.checkNotBoostrap(clazz);
        return (Function<T, R>) this.sendRemote(clazz);
    }

    public Runnable sendRunnable (final Class<? extends Runnable> clazz) {
        if (clazz.getClassLoader() == null) {
            return () -> {
                try {
                    execClass(clazz.getName());
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        return this.sendRemote(clazz);
    }

    private Remote sendRemote (final Class<?> clazz) {
        this.ensureUploaded(clazz);
        return clRunnable.get(clazz.getName());
    }

    public void ensureUploaded (Class<?> clazz) {
        while (clazz != null) {
            final Class<?> old = clazz;
            clazz = clazz.getDeclaringClass();
            if (clazz == null) {
                this.ensureUploaded0(old);
            }
        }
    }

    private void ensureUploaded0 (final Class<?> clazz) {
        if (clazz.getClassLoader() == null ||
                clRunnable.containsKey(clazz.getName())) return;
        this.ensureUploaded0(clazz.getSuperclass());
        for (Class<?> cl:clazz.getDeclaredClasses()) {
            this.ensureUploaded0(cl);
        }
        clRunnable.computeIfAbsent (clazz.getName(), name -> {
            final InputStream is = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class");
            try {
                return (Remote) sendStream(is, clazz.getName(), false);
            } catch (final IOException e) {
                IOHelper.sneakyThrow(e);
                return null;
            }
        });
    }

    public Runnable sendFile (final File file,final String className) throws IOException {
        try (final FileInputStream fis = new FileInputStream(file)) {
            return this.sendStream(fis, className, false);
        }
    }

    public Runnable sendStream (final InputStream is, final String name, final boolean execute) throws IOException {
        return this.sendData(IOHelper.readAllBytes(is), name, execute);
    }

    public Runnable sendData (final byte[] buffer, final String name, final boolean execute) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(execute ? ID_SEND_EXEC_DATA : ID_SEND_DATA);
            this.dos.writeInt(buffer.length);
            IOHelper.writeString(this.dos, name);
            this.dos.write(buffer);
            this.dos.flush();
        }
        return new Remote(name);
    }

    public void execData (final String name) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(ID_EXEC_DATA);
            IOHelper.writeString(this.dos, name);
            this.dos.flush();
            if (dis.readByte() != 0) {
                throw new IOException("Data " + name + " was not sent yet");
            }
        }
    }

    public void execClass (final String name) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(ID_EXEC_CLASS);
            IOHelper.writeString(this.dos, name);
            this.dos.flush();
        }
    }

    public int execDataBlocking (final String name) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(ID_EXEC_DATA_BLOCKING);
            IOHelper.writeString(this.dos, name);
            this.dos.flush();
            if (this.dis.readByte() != 0) {
                throw new IOException("Data " + name + " was not sent yet");
            }
            return this.dis.readInt();
        }
    }

    public void execFunc (final String name,final Object param) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(ID_EXEC_DATA_FUNC);
            IOHelper.writeString(this.dos, name);
            IOHelper.serialize(this.dos, this.oos, param, this.remoteProtocol);
            this.dos.flush();
            if (this.dis.readByte() != 0) {
                throw new IOException("Data " + name + " was not sent yet");
            }
        }
    }

    public Object execFuncBlocking (final String name,final Object param) throws IOException {
        synchronized (this.writeLock) {
            this.lastWrite = System.currentTimeMillis();
            this.dos.writeInt(ID_EXEC_DATA_FUNC_BLOCKING);
            IOHelper.writeString(this.dos, name);
            IOHelper.serialize(this.dos, this.oos, param, this.remoteProtocol);
            this.dos.flush();
            if (this.dis.readByte() != 0) {
                throw new IOException("Data " + name + " was not sent yet");
            }
            try {
                return IOHelper.deserialize(this.dis, this.ois, DynamicClient.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
    }

    public void sendKeepAlive () throws IOException {
        synchronized (this.writeLock) {
            this.dos.writeInt(ID_KEEP_ALIVE);
            this.dos.flush();
        }
    }

    public boolean canRemoteClose () {
        return (this.remoteFlags & FLAG_ALLOW_REMOTE_CLOSE) != 0;
    }

    public void remoteCloseServer () throws IOException {
        synchronized (this.writeLock) {
            this.dos.writeInt(ID_CLOSE_SERVER);
            this.dos.flush();
            if (this.dis.readByte() != 0) {
                throw new SecurityException("Client is not allowed to close the server");
            }
        }
        this.close();
    }

    public void blockActions (int... actions) throws IOException {
        this.blockActionsRaw(0, actions);
    }

    public void blockActionsRaw (int maxCallID, int... actions) throws IOException {
        synchronized (this.writeLock) {
            this.dos.writeInt(ID_SEC_BLOCK_ACTIONS);
            this.dos.writeInt(maxCallID);
            IOHelper.writeInts(this.dos, actions);
            this.dos.flush();
        }
    }

    public boolean allowUnsafeSerialisation () {
        return (this.remoteFlags & FLAG_ALLOW_UNSAFE_SERIALISATION) != 0;
    }

    public int getRemoteProtocol() {
        return remoteProtocol;
    }

    @Override
    public void close () throws IOException {
        if (this.socket != null) this.socket.close();
    }

    public boolean isClosed () {
        return this.socket == null || this.socket.isClosed();
    }
}
