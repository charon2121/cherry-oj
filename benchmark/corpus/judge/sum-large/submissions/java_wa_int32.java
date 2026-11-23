import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        int count = in.nextInt();
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += in.nextLong();
        }
        System.out.println(total);
    }
}
