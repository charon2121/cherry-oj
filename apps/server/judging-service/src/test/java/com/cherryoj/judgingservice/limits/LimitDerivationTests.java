package com.cherryoj.judgingservice.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** docs/protocols/limits.md 第 8 节的黄金用例：实现必须逐位一致。 */
class LimitDerivationTests {
    private static final long MS = 1_000_000L;
    private static final long MIB = 1L << 20;
    private static final LimitModel CPP = new LimitModel(1000, null, 0, 0, 1000, 0, true);
    /** 协议第 8 节的 Java 初始参数；Java 还不在语言目录里，只在测试里用。 */
    private static final LimitModel JAVA = new LimitModel(3200, null, 170 * MS, 0, 1250, 40 * MIB, true);

    private static final long CAP_CPU = 20_000 * MS;
    private static final long CAP_MEMORY = 1024 * MIB;

    @Test
    void cppBaseDerivesJava() {
        var derived = LimitDerivation.derive(CPP, 1000 * MS, 256 * MIB, JAVA, 0, CAP_CPU, CAP_MEMORY);
        assertThat(derived.cpuNs()).isEqualTo(3370 * MS);
        assertThat(derived.memoryBytes()).isEqualTo(360 * MIB);
        assertThat(derived.refCpuNs()).isEqualTo(1000 * MS);
        assertThat(derived.refMemoryBytes()).isEqualTo(256 * MIB);
        assertThat(derived.cpuCapped()).isFalse();
        assertThat(derived.memoryCapped()).isFalse();
    }

    @Test
    void javaBaseInvertsBackToCpp() {
        var derived = LimitDerivation.derive(JAVA, 3370 * MS, 360 * MIB, CPP, 0, CAP_CPU, CAP_MEMORY);
        assertThat(derived.cpuNs()).isEqualTo(1000 * MS);
        assertThat(derived.memoryBytes()).isEqualTo(256 * MIB);
        assertThat(derived.refCpuNs()).isEqualTo(1000 * MS);
        assertThat(derived.refMemoryBytes()).isEqualTo(256 * MIB);
    }

    @Test
    void tinyBaseLimitsAreFlooredBeforeDeriving() {
        // T_ref 取下限 10ms：3.2 × 10ms + 170ms = 202ms，向上取整到 210ms；M_ref 取下限 16MiB：1.25 × 16 + 40 = 60MiB
        var derived = LimitDerivation.derive(CPP, 5 * MS, 16 * MIB, JAVA, 0, CAP_CPU, CAP_MEMORY);
        assertThat(derived.cpuNs()).isEqualTo(210 * MS);
        assertThat(derived.memoryBytes()).isEqualTo(60 * MIB);
        assertThat(derived.refCpuNs()).isEqualTo(10 * MS);
    }

    @Test
    void valuesAbovePlatformLimitsAreCappedAndMarked() {
        // 3.2 × 8s + 170ms = 25.77s，1.25 × 1GiB + 40MiB = 1320MiB：都截到平台上限，语言照样可用
        var derived = LimitDerivation.derive(CPP, 8000 * MS, 1024 * MIB, JAVA, 0, CAP_CPU, CAP_MEMORY);
        assertThat(derived.cpuNs()).isEqualTo(CAP_CPU);
        assertThat(derived.memoryBytes()).isEqualTo(CAP_MEMORY);
        assertThat(derived.cpuCapped()).isTrue();
        assertThat(derived.memoryCapped()).isTrue();
    }

    @Test
    void ioTermUsesPicosecondsPerByteOnBothSides() {
        // 基础语言 X 每字节 2000ps，目标 L 每字节 5000ps，IO 1MB：
        // T_ref = (1s − 2ms) / 1.0 = 998ms；T_L = 998ms + 5ms = 1.003s，向上取整到 1.01s
        var withIo = new LimitModel(1000, null, 0, 2000, 1000, 0, true);
        var target = new LimitModel(1000, null, 0, 5000, 1000, 0, false);
        var derived = LimitDerivation.derive(withIo, 1000 * MS, 256 * MIB, target, 1_000_000, CAP_CPU, CAP_MEMORY);
        assertThat(derived.refCpuNs()).isEqualTo(998 * MS);
        assertThat(derived.cpuNs()).isEqualTo(1010 * MS);
    }

    @Test
    void theCompensationCapAppliesOnlyWhenDerivingNotWhenInverting() {
        var capped = new LimitModel(6800, 5000L, 130 * MS, 0, 1000, 8 * MIB, false);
        // 推导到 capped：用 5.0 而不是 6.8
        assertThat(LimitDerivation.derive(CPP, 1000 * MS, 256 * MIB, capped, 0, CAP_CPU, CAP_MEMORY)
                        .cpuNs())
                .isEqualTo(5130 * MS);
        // 以 capped 为基础反推：用原始的 6.8
        assertThat(LimitDerivation.derive(capped, 6930 * MS, 264 * MIB, CPP, 0, CAP_CPU, CAP_MEMORY)
                        .refCpuNs())
                .isEqualTo(1000 * MS);
    }

    @Test
    void invalidInputsAreRejectedRatherThanGuessed() {
        assertThatThrownBy(() -> LimitDerivation.derive(CPP, 0, 256 * MIB, JAVA, 0, CAP_CPU, CAP_MEMORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LimitDerivation.derive(CPP, 1000 * MS, 256 * MIB, JAVA, -1, CAP_CPU, CAP_MEMORY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LimitDerivation.derive(CPP, 1000 * MS, 256 * MIB, JAVA, 0, 0, CAP_MEMORY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
