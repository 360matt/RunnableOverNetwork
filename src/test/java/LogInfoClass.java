public class LogInfoClass implements Runnable {
    @Override
    public void run() {
        System.out.println("Loader: " + LogInfoClass.class.getClassLoader().getClass().getName());
        System.out.println("Thread: " + Thread.currentThread().getName());
        new Exception("Stack printer").printStackTrace(System.out);
    }
}
