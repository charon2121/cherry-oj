import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.TreeSet;
public class Main {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        int count = Integer.parseInt(reader.readLine());
        var tokens = new StringTokenizer(reader.readLine());
        var numbers = new TreeSet<Long>();
        for (int i = 0; i < count; i++) {
            numbers.add(Long.parseLong(tokens.nextToken()));
        }
        var output = new StringJoiner(" ");
        for (long number : numbers) {
            output.add(Long.toString(number));
        }
        System.out.println(output);
    }
}
