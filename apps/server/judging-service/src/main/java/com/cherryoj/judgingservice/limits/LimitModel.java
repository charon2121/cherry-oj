package com.cherryoj.judgingservice.limits;

/**
 * 一门语言的限制参数，字段与取值范围以 contracts/languages.json 的 limitModel 和 docs/protocols/limits.md 第 3 节为准。
 * 倍数用千分比整数，cpuFactorCapPermille 为 null 表示不设补偿上限。
 */
public record LimitModel(
        long cpuFactorPermille,
        Long cpuFactorCapPermille,
        long cpuOverheadNs,
        long ioPicosPerByte,
        long memoryFactorPermille,
        long memoryOverheadBytes,
        boolean baseEligible) {
    public LimitModel {
        if (cpuFactorPermille < 100
                || cpuFactorPermille > 100_000
                || (cpuFactorCapPermille != null && (cpuFactorCapPermille < 100 || cpuFactorCapPermille > 100_000))
                || cpuOverheadNs < 0
                || ioPicosPerByte < 0
                || memoryFactorPermille < 1000
                || memoryFactorPermille > 10_000
                || memoryOverheadBytes < 0) {
            throw new IllegalArgumentException("limit model out of protocol range");
        }
    }

    /** 推导到这门语言时实际使用的倍数：补偿上限只在推导时生效，反推一律用原始倍数。 */
    long effectiveCpuFactorPermille() {
        return cpuFactorCapPermille == null ? cpuFactorPermille : Math.min(cpuFactorPermille, cpuFactorCapPermille);
    }
}
