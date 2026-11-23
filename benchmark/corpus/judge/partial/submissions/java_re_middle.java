import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        long a = in.nextLong();
        long b = in.nextLong();
        if (a == 10) {
            throw new IllegalArgumentException("benchmark runtime error");
        }
        System.out.println(a + b);
    }
}
