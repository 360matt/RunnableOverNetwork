public class TimerClass implements Runnable {
    private static long time;

    @Override
    public void run() {
        if (time == 0) {
            System.out.println("Start timer...");
            time = System.currentTimeMillis();
        } else {
            System.out.println("Took "+ (System.currentTimeMillis() - time)+ "ms");
        }
    }
}
