import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        long a = in.nextLong();
        long b = in.nextLong();
        if (a == 1 && b == 1) {
            b = 0;
        }
        System.out.println(a + b);
    }
}
