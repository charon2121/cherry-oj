import java.io.*;
import java.util.*;
import java.math.*;

class Solution {
    private record Operands(long a, long b) {}

    long add(long a, long b) {
        var operands = new Operands(a, b);
        java.util.function.LongSupplier sum = new java.util.function.LongSupplier() {
            @Override
            public long getAsLong() {
                return operands.a() + operands.b();
            }
        };
        return sum.getAsLong();
    }
}
