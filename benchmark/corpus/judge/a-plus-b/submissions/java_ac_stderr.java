import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        long a = in.nextLong();
        long b = in.nextLong();
        System.err.println("用于 Benchmark 的日志");
        System.out.println(a + b);
    }
}
