import fr.i360matt.runnableOverNetwork.DynamicClient;

import java.io.IOException;

public class Example {


    public static void main (final String[] args) throws IOException {

        /*
        new Thread(() -> {
            try {
                new DynamicServer(5000);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

         */

        final DynamicClient client = new DynamicClient("127.0.0.1", 5000);

        client.sendRunnable(OneClass.class);

        client.close();

    }
}
