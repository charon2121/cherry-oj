import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var in = new Scanner(System.in);
        int n = in.nextInt();
        var output = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            output.append(i).append('\n');
        }
        System.out.print(output);
    }
}
