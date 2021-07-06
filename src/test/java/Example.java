import fr.i360matt.runnableOverNetwork.DynamicClient;
import fr.i360matt.runnableOverNetwork.DynamicServer;

import java.io.IOException;

public class Example {


    public static void main (final String[] args) throws IOException {
        System.out.println("Start");
        final DynamicServer dynamicServer = new DynamicServer(5000, "password");
        /* For the test to show meaningfull result we need to .setIsolated(true)
        Because without it it would get the class from this class loader
        * */
        dynamicServer.setIsolated(true);
        dynamicServer.setAllowRemoteClose(true); // For testing
        new Thread(() -> {
            try {
                dynamicServer.listen();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }).start();

        final DynamicClient client = new DynamicClient("127.0.0.1", 5000, "password");

        client.execRunnable(TimerClass.class);
        client.execRunnable(LogInfoClass.class);
        client.execRunnable(OneClass.class);
        client.execRunnable(TimerClass.class);

        client.remoteCloseServer();
    }
}
