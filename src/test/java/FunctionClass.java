import java.util.function.Function;

public class FunctionClass implements Function<String, String> {
    @Override
    public String apply(String s) {
        return System.getProperty(s);
    }
}
