package com.cherryoj.judgingservice.limits;

import java.math.BigInteger;

/**
 * docs/protocols/limits.md 第 4 节的推导公式。全程用整数有理数计算，只在最后一步向上取整，
 * 保证与协议里的黄金用例逐位一致；时间在内部以皮秒计，因为 IO 参数的单位是皮秒每字节。
 */
public final class LimitDerivation {
    static final long REF_CPU_MIN_NS = 10_000_000L;
    static final long REF_MEMORY_MIN_BYTES = 16L << 20;
    private static final long CPU_STEP_NS = 10_000_000L;
    private static final long MEMORY_STEP_BYTES = 1L << 20;
    private static final BigInteger PS_PER_NS = BigInteger.valueOf(1000);
    private static final BigInteger PERMILLE = BigInteger.valueOf(1000);

    private LimitDerivation() {}

    /**
     * 从基础语言 X 的限制推导到语言 L。
     *
     * @param maxTestcaseIoBytes 题目当前测试数据中单个测试点输入加输出的最大字节数
     */
    public static Derived derive(
            LimitModel base,
            long baseCpuNs,
            long baseMemoryBytes,
            LimitModel target,
            long maxTestcaseIoBytes,
            long platformMaxCpuNs,
            long platformMaxMemoryBytes) {
        if (baseCpuNs < 1 || baseMemoryBytes < 1 || maxTestcaseIoBytes < 0) {
            throw new IllegalArgumentException("invalid base limits");
        }
        if (platformMaxCpuNs < 1 || platformMaxMemoryBytes < 1) {
            throw new IllegalArgumentException("invalid platform limits");
        }
        BigInteger io = BigInteger.valueOf(maxTestcaseIoBytes);

        // T_ref = max((T_X − b_X − c_X × IO) / a_X, 下限)，以皮秒计的分数 refCpu / refCpuDen
        BigInteger refCpuDen = BigInteger.valueOf(base.cpuFactorPermille());
        BigInteger refCpu = BigInteger.valueOf(baseCpuNs)
                .subtract(BigInteger.valueOf(base.cpuOverheadNs()))
                .multiply(PS_PER_NS)
                .subtract(BigInteger.valueOf(base.ioPicosPerByte()).multiply(io))
                .multiply(PERMILLE);
        BigInteger refCpuFloor =
                BigInteger.valueOf(REF_CPU_MIN_NS).multiply(PS_PER_NS).multiply(refCpuDen);
        if (refCpu.compareTo(refCpuFloor) < 0) refCpu = refCpuFloor;

        // T_L = min(a_L, A_max_L) × T_ref + b_L + c_L × IO，向上取整到 10ms
        BigInteger targetFixedPs = BigInteger.valueOf(target.cpuOverheadNs())
                .multiply(PS_PER_NS)
                .add(BigInteger.valueOf(target.ioPicosPerByte()).multiply(io));
        BigInteger cpuDen = PERMILLE.multiply(refCpuDen);
        BigInteger cpuNum = BigInteger.valueOf(target.effectiveCpuFactorPermille())
                .multiply(refCpu)
                .add(targetFixedPs.multiply(cpuDen));
        BigInteger cpuNs = ceilToStep(cpuNum, cpuDen.multiply(PS_PER_NS), CPU_STEP_NS);

        // M_ref = max((M_X − m_X) / k_X, 下限)，分数 refMemory / refMemoryDen
        BigInteger refMemoryDen = BigInteger.valueOf(base.memoryFactorPermille());
        BigInteger refMemory = BigInteger.valueOf(baseMemoryBytes)
                .subtract(BigInteger.valueOf(base.memoryOverheadBytes()))
                .multiply(PERMILLE);
        BigInteger refMemoryFloor = BigInteger.valueOf(REF_MEMORY_MIN_BYTES).multiply(refMemoryDen);
        if (refMemory.compareTo(refMemoryFloor) < 0) refMemory = refMemoryFloor;

        // M_L = k_L × M_ref + m_L，向上取整到 1MiB
        BigInteger memoryDen = PERMILLE.multiply(refMemoryDen);
        BigInteger memoryNum = BigInteger.valueOf(target.memoryFactorPermille())
                .multiply(refMemory)
                .add(BigInteger.valueOf(target.memoryOverheadBytes()).multiply(memoryDen));
        BigInteger memoryBytes = ceilToStep(memoryNum, memoryDen, MEMORY_STEP_BYTES);

        // 超过平台上限就取上限，语言照样可用；截断写进结果，冻结时一并记下。
        boolean cpuCapped = cpuNs.compareTo(BigInteger.valueOf(platformMaxCpuNs)) > 0;
        boolean memoryCapped = memoryBytes.compareTo(BigInteger.valueOf(platformMaxMemoryBytes)) > 0;
        return new Derived(
                cpuCapped ? platformMaxCpuNs : cpuNs.longValueExact(),
                memoryCapped ? platformMaxMemoryBytes : memoryBytes.longValueExact(),
                ceilDiv(refCpu, refCpuDen.multiply(PS_PER_NS)).longValueExact(),
                ceilDiv(refMemory, refMemoryDen).longValueExact(),
                cpuCapped,
                memoryCapped);
    }

    /** num / den 换算成 step 的整数倍，向上取整。 */
    private static BigInteger ceilToStep(BigInteger num, BigInteger den, long step) {
        return ceilDiv(num, den.multiply(BigInteger.valueOf(step))).multiply(BigInteger.valueOf(step));
    }

    private static BigInteger ceilDiv(BigInteger num, BigInteger den) {
        BigInteger[] qr = num.divideAndRemainder(den);
        return qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0];
    }

    /**
     * 推导结果。refCpuNs、refMemoryBytes 是 C++ 当量向上取整后的值，只用于展示与冻结记录；
     * 复算以原始输入为准。
     */
    public record Derived(
            long cpuNs,
            long memoryBytes,
            long refCpuNs,
            long refMemoryBytes,
            boolean cpuCapped,
            boolean memoryCapped) {}
}
