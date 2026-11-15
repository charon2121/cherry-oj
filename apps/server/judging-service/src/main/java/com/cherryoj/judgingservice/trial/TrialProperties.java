package com.cherryoj.judgingservice.trial;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 各层期限见 docs/protocols/custom-run.md，降低准入上限不要求调整外层等待窗口。 */
@ConfigurationProperties("cherry.custom-run")
public record TrialProperties(Duration maxExecutionBudget) {
    public static final Duration DEADLINE = Duration.ofSeconds(195);
    public static final Duration MAX_EXECUTION_BUDGET = Duration.ofSeconds(180);

    public TrialProperties {
        if (maxExecutionBudget == null
                || maxExecutionBudget.isNegative()
                || maxExecutionBudget.isZero()
                || maxExecutionBudget.compareTo(MAX_EXECUTION_BUDGET) > 0)
            throw new IllegalArgumentException("custom-run.max-execution-budget must be > 0 and <= 180s");
    }
}
