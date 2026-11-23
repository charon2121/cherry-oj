import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        int sizeMiB = Integer.parseInt(reader.readLine());
        if (sizeMiB >= 100) {
            sizeMiB = 600;
        }
        List<byte[]> blocks = new ArrayList<>();
        for (int i = 0; i < sizeMiB; i++) {
            byte[] block = new byte[1 << 20];
            for (int offset = 0; offset < block.length; offset += 4096) {
                block[offset] = 1;
            }
            blocks.add(block);
        }
        System.out.println(blocks.size());
    }
}
