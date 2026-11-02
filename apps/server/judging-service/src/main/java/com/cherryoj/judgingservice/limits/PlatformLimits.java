package com.cherryoj.judgingservice.limits;

import com.cherryoj.judgingservice.formal.FormalProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 平台上限，启动时与墙钟推算规则一起核对。 */
@Component
public class PlatformLimits {
    /** 与 judge 执行层的单次执行墙钟硬界一致（apps/judge-engine 的 backend.MaxClockNs）。 */
    static final Duration JUDGE_MAX_CLOCK = Duration.ofSeconds(120);

    private final long maxCpuNs;
    private final long maxMemoryBytes;

    public PlatformLimits(LimitsProperties limits, FormalProperties formal) {
        // 推导值不带 clockNs，墙钟按 cpuNs × wall-ratio 推算；上限推出的墙钟必须落在 judge 能接受的范围内。
        if (Math.multiplyHigh(limits.maxCpuNs(), formal.wallRatio()) != 0
                || limits.maxCpuNs() * formal.wallRatio() > JUDGE_MAX_CLOCK.toNanos()) {
            throw new IllegalArgumentException(
                    "cherry.judging.limits.max-cpu-ns × cherry.formal.wall-ratio exceeds the judge clock bound");
        }
        this.maxCpuNs = limits.maxCpuNs();
        this.maxMemoryBytes = limits.maxMemoryBytes();
    }

    public long maxCpuNs() {
        return maxCpuNs;
    }

    public long maxMemoryBytes() {
        return maxMemoryBytes;
    }
}
