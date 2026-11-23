import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringJoiner;
import java.util.StringTokenizer;
public class Main {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        int count = Integer.parseInt(reader.readLine());
        var tokens = new StringTokenizer(reader.readLine());
        long[] numbers = new long[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = Long.parseLong(tokens.nextToken());
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int last = numbers.length - 1; last > 0; last--) {
                for (int i = 0; i < last; i++) {
                    if (numbers[i] > numbers[i + 1]) {
                        long value = numbers[i];
                        numbers[i] = numbers[i + 1];
                        numbers[i + 1] = value;
                    }
                }
            }
        }
        var output = new StringJoiner(" ");
        for (long number : numbers) {
            output.add(Long.toString(number));
        }
        System.out.println(output);
    }
}
