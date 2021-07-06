package fr.i360matt.runnableOverNetwork;

import java.io.*;
import java.net.*;

public class DynamicServer implements Closeable {

    private final ServerSocket server;
    private final String password;

    public DynamicServer (int port, final String password) throws IOException {
        this.server = new ServerSocket(port);
        this.password = password;
        while (!server.isClosed()) {
            Socket clientSock = server.accept();
            hasNewClient(clientSock);
        }
    }

    public DynamicServer (final ServerSocket server, final String password) throws IOException {
        this.server = server;
        this.password = password;
        while (!server.isClosed()) {
            Socket clientSock = server.accept();
            hasNewClient(clientSock);
        }
    }

    private void hasNewClient (final Socket clientSock)  {
        new Thread(() -> {
            try {
                final DataInputStream receiver = new DataInputStream(clientSock.getInputStream());
                if (!receiver.readUTF().equals(this.password)) {
                    return;
                }

                while (!this.server.isClosed() && !clientSock.isClosed()) {

                    int filesize = receiver.readInt(); // Send file size in separate msg
                    if (filesize == 0)
                        continue;


                    int read = 0;
                    int totalRead = 0;
                    int remaining = filesize;


                    final File dir = new File("./runDir");
                    if (!dir.exists()) {
                        dir.mkdir();
                    }

                    final File file = new File(dir, receiver.readUTF());
                    final FileOutputStream fos = new FileOutputStream(file);

                    byte[] buffer = new byte[filesize];
                    while((read = receiver.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                        totalRead += read;
                        remaining -= read;
                        System.out.println("read " + totalRead + " bytes.");
                        fos.write(buffer, 0, read);
                    }
                    fos.close();


                    this.execRunnable(dir, file);

                }
            } catch (final Exception ignored) {
                try {
                    clientSock.close();
                } catch (IOException ignored1) { }
            }
        }).start();
    }


    private void execRunnable (final File dir, final File file) throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        final URLClassLoader classLoader = new URLClassLoader(new URL[]{dir.toURI().toURL()});
        // Load the class from the classloader by name....
        final  Class<?> loadedClass = classLoader.loadClass(file.getName().replace(".class", ""));
        // Create a new instance...
        final Object obj = loadedClass.newInstance();
        // Santity check
        if (obj instanceof Runnable) {
            // Cast to the DoStuff interface
            final Runnable stuffToDo = (Runnable)obj;
            // Run it baby
            stuffToDo.run();
        }
    }

    @Override
    public void close () throws IOException {
        this.server.close();
    }
}
