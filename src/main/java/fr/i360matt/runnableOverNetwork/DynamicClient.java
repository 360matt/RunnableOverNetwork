package fr.i360matt.runnableOverNetwork;


import java.io.*;
import java.net.Socket;

public class DynamicClient implements Closeable {

    private final DataOutputStream dos;

    public DynamicClient (String host, int port, final String password) throws IOException {
        final Socket socket = new Socket(host, port);
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.dos.writeUTF(password);
    }

    public DynamicClient (final Socket socket, final String password) throws IOException {
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.dos.writeUTF(password);
    }

    public void sendRunnable (final Class<? extends Runnable> clazz) throws IOException {
        final String dir = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        final File file = new File(dir + "\\" + clazz.getName() + ".class");
        this.sendFile(file);
    }

    public void sendFile (final File file) throws IOException {
        final FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];

        dos.writeInt((int) file.length());
        dos.writeUTF(file.getName());

        while (fis.read(buffer) > 0)
            dos.write(buffer);
        dos.flush();

        fis.close();
    }

    @Override
    public void close () throws IOException {
        if (dos != null) dos.close();
    }
}
