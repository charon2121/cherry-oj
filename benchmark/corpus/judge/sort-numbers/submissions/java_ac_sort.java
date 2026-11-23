import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.Arrays;
public class Main {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        int count = Integer.parseInt(reader.readLine());
        var tokens = new StringTokenizer(reader.readLine());
        long[] numbers = new long[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = Long.parseLong(tokens.nextToken());
        }
        Arrays.sort(numbers);
        var output = new StringJoiner(" ");
        for (long number : numbers) {
            output.add(Long.toString(number));
        }
        System.out.println(output);
    }
}
