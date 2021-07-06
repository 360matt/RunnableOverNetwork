import fr.i360matt.runnableOverNetwork.DynamicClient;

import java.io.IOException;

public class Example {


    public static void main (final String[] args) throws IOException {

        /*
        new Thread(() -> {
            try {
                new DynamicServer(5000, "password");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

         */

        final DynamicClient client = new DynamicClient("127.0.0.1", 5000, "password");

        client.sendRunnable(OneClass.class);

        client.close();

    }
}
