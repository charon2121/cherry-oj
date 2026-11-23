import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        if (in.hasNextLong()) {
            long sum = 0;
            while (in.hasNextLong()) {
                sum += in.nextLong();
            }
            System.out.println(sum);
        }
    }
}
