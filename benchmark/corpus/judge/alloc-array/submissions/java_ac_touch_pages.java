import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        int sizeMiB = Integer.parseInt(reader.readLine());
        int written = 0;
        for (int i = 0; i < sizeMiB; i++) {
            byte[] block = new byte[1 << 20];
            for (int offset = 0; offset < block.length; offset += 4096) {
                block[offset] = 1;
            }
            written += block[0];
        }
        System.out.println(written);
    }
}
