import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        long a = in.nextLong();
        long b = in.nextLong();
        if (Math.abs(a) >= 1_000_000) {
            while (true) {
                Thread.onSpinWait();
            }
        }
        System.out.println(a + b);
    }
}
