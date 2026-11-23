package com.cherryoj.judgingservice.limits;

import java.time.LocalDateTime;

/** 生效的绝对限制及其来源。推导时 calibrationId 和 calibratedAt 来自基础标定。 */
public record ResolvedLanguageLimits(
        String calibrationId,
        long cpuNs,
        long memoryBytes,
        Long clockNs,
        String limitSource,
        Derivation limitDerivation,
        LocalDateTime calibratedAt) {

    /** 随提交冻结的推导依据，字段见 contracts/execution-profile.schema.json。 */
    public record Derivation(
            String baseLanguageId,
            String baseCalibrationId,
            long baseCpuNs,
            long baseMemoryBytes,
            long maxTestcaseIoBytes,
            long refCpuNs,
            long refMemoryBytes,
            long platformMaxCpuNs,
            long platformMaxMemoryBytes,
            boolean cpuCapped,
            boolean memoryCapped,
            ModelSnapshot baseLimitModel,
            ModelSnapshot languageLimitModel) {}

    /** 推导使用的参数原值，不读取运行中的语言目录。 */
    public record ModelSnapshot(
            long cpuFactorPermille,
            Long cpuFactorCapPermille,
            long cpuOverheadNs,
            long ioPicosPerByte,
            long memoryFactorPermille,
            long memoryOverheadBytes) {}
}
