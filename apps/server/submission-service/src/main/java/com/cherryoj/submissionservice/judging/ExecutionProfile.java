package com.cherryoj.submissionservice.judging;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 判题服务解析的资源限制与执行预算，字段见 contracts/execution-profile.schema.json。 */
public record ExecutionProfile(
        String problemId,
        String languageId,
        String languageCalibrationId,
        JudgeLimits effectiveLimits,
        String limitSource,
        LimitDerivation limitDerivation,
        long executionBudgetNs) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JudgeLimits(long cpuNs, long memoryBytes, Long clockNs) {}

    /** 推导时所用的语言限制参数原值，契约见 contracts/execution-profile.schema.json 的 LimitModel。 */
    public record LimitModel(
            long cpuFactorPermille,
            Long cpuFactorCapPermille,
            long cpuOverheadNs,
            long ioPicosPerByte,
            long memoryFactorPermille,
            long memoryOverheadBytes) {}

    /** DERIVED 的推导依据（docs/protocols/limits.md 第 6 节），原样冻结进 JudgeInput，供追溯与复算。 */
    public record LimitDerivation(
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
            LimitModel baseLimitModel,
            LimitModel languageLimitModel) {}
}
