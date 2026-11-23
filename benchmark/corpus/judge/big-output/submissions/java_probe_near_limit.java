public class Main {
    public static void main(String[] args) throws Exception {
        String block = "x".repeat(4096);
        for (int i = 0; i < 255; i++) {
            System.out.print(block);
        }
    }
}
