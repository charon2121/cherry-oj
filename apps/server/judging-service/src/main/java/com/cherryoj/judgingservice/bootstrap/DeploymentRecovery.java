package com.cherryoj.judgingservice.bootstrap;

import com.cherryoj.judgingservice.config.JudgingProperties;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import com.cherryoj.judgingservice.storage.TestDataDeploymentStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public final class DeploymentRecovery implements ApplicationRunner {
    private final JudgingProperties properties;
    private final JudgingRepository repository;
    private final TestDataDeploymentStore store;

    public DeploymentRecovery(JudgingProperties properties, JudgingRepository repository,
                              TestDataDeploymentStore store) {
        this.properties = properties;
        this.repository = repository;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments ignored) {
        if (properties.recoveryEnabled()) store.recover(repository.listReadyTestDataVersionIds());
    }
}
