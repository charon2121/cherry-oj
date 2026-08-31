package com.cherryoj.problemservice.bootstrap;

import com.cherryoj.problemservice.config.ProblemValidationProperties;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "cherry.problem.validation", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
public class ProblemValidationRecovery implements ApplicationRunner {
    private final AdminProblemMapper mapper;
    private final ProblemValidationProperties properties;
    private final Clock clock;

    public ProblemValidationRecovery(
            AdminProblemMapper mapper,
            ProblemValidationProperties properties,
            Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        mapper.recoverStaleValidations(now.minus(properties.staleAge()), now);
    }
}
