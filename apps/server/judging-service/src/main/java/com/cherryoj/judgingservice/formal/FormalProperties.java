package com.cherryoj.judgingservice.formal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.formal")
public record FormalProperties(boolean enabled, String requestsTopic, String lifecycleTopic, String submissionUrl,
                               String submissionToken, int parallelism, int maxAttempts, Duration leaseDuration,
                               Duration deadline, Duration compileBudget, long wallRatio, Duration caseOverhead) {
    public FormalProperties {
        if (parallelism<1 || parallelism>32 || maxAttempts<1 || maxAttempts>10 || wallRatio<1
                || leaseDuration==null || leaseDuration.compareTo(Duration.ofSeconds(5))<0
                || deadline==null || deadline.isNegative() || deadline.isZero()
                || compileBudget==null || compileBudget.isNegative() || compileBudget.isZero()
                || caseOverhead==null || caseOverhead.isNegative() || caseOverhead.isZero()) throw new IllegalArgumentException("invalid formal judging budgets");
        if (enabled) com.cherryoj.identitysecurity.service.ServiceCredentials.validate(submissionToken);
    }
    public Duration executionBudget(FormalInput input) {
        return executionBudget(input.effectiveLimits().cpuNs(),input.effectiveLimits().clockNs(),input.totalCount());
    }
    public Duration executionBudget(long cpuNs,Long clockNs,int totalCount) {
        if(totalCount<1 || totalCount>1000) throw new IllegalArgumentException("invalid case count");
        long clock=clockNs!=null?clockNs:Math.multiplyExact(cpuNs,wallRatio);
        return compileBudget.plusNanos(Math.multiplyExact(Math.addExact(clock,caseOverhead.toNanos()),totalCount));
    }
}
