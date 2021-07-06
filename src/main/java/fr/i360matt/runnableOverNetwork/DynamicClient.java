package fr.i360matt.runnableOverNetwork;


import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicClient implements Closeable,ConnectionConstants {

    private final Object writeLock = new Object();
    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;
    private final ConcurrentHashMap<String, Runnable> clRunnable;
    private long lastWrite;

    public DynamicClient (String host, int port, final String password) throws IOException {
        this(new Socket(host, port), password);
    }

    public DynamicClient (final Socket socket, final String password) throws IOException {
        socket.setTcpNoDelay(true);
        this.socket = socket;
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.dis = new DataInputStream(socket.getInputStream());
        IOHelper.writeString(this.dos, password);
        if (this.dis.readByte() != 0) {
            throw new IOException("Invalid password!");
        }
        this.clRunnable = new ConcurrentHashMap<>();
        this.startKeepAlive();
    }

    private void startKeepAlive() {
        this.lastWrite = System.currentTimeMillis();
        new Thread() {
            @Override
            public void run() {
                try {
                    while (!this.isInterrupted() && !socket.isClosed()) {
                        long current = System.currentTimeMillis();
                        if (lastWrite + 5000 < current) {
                            sendKeepAlive();
                        }
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void execRunnable (final Class<? extends Runnable> clazz) throws IOException {
        this.sendRunnable(clazz).run();
    }

    public Runnable sendRunnable (final Class<? extends Runnable> clazz) throws IOException {
        if (clazz.getClassLoader() == null) {
            return () -> {
                try {
                    execClass(clazz.getName());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        return clRunnable.computeIfAbsent(clazz.getName(), name -> {
            InputStream is = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class");
            try {
                return sendStream(is, clazz.getName(), false);
            } catch (IOException e) {
                IOHelper.sneakyThrow(e);
                return null;
            }
        });
    }

    public Runnable sendFile (final File file,final String className) throws IOException {
        try(final FileInputStream fis = new FileInputStream(file)) {
            return this.sendStream(fis, className, false);
        }
    }

    public Runnable sendStream(final InputStream is,String name,boolean execute) throws IOException {
        return this.sendData(IOHelper.readAllBytes(is), name, execute);
    }

    public Runnable sendData(byte[] buffer,String name,boolean execute) throws IOException {
        synchronized (writeLock) {
            dos.writeInt(execute ? ID_SEND_EXEC_DATA : ID_SEND_DATA);
            dos.writeInt(buffer.length);
            IOHelper.writeString(dos, name);
            dos.write(buffer);
            dos.flush();
        }
        return () -> {
            try {
                execData(name);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public void execData(String name) throws IOException {
        synchronized (writeLock) {
            dos.writeInt(ID_EXEC_DATA);
            IOHelper.writeString(dos, name);
            dos.flush();
        }
            if (dis.readByte() != 0) {
                throw new IOException("Data " + name + " was not sent yet");
            }

    }

    public void execClass(String name) throws IOException {
        synchronized (writeLock) {
            dos.writeInt(ID_EXEC_CLASS);
            IOHelper.writeString(dos, name);
            dos.flush();
        }
    }

    public void sendKeepAlive() throws IOException {
        synchronized (writeLock) {
            dos.writeInt(ID_KEEP_ALIVE);
            dos.flush();
        }
    }

    public void remoteCloseServer() throws IOException {
        synchronized (writeLock) {
            dos.writeInt(ID_CLOSE_SERVER);
            dos.flush();
        }
        if (dis.readByte() != 0) {
            throw new SecurityException("Client is not allowed to close the server");
        }
        this.close();
    }

    @Override
    public void close () throws IOException {
        if (socket != null) socket.close();
    }

    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }
}
