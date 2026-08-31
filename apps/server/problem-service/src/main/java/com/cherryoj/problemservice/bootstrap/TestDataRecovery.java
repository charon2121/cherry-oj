package com.cherryoj.problemservice.bootstrap;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.persistence.TestDataMapper;
import com.cherryoj.problemservice.storage.TestDataAssetStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        prefix = "cherry.problem.test-data", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
public class TestDataRecovery implements ApplicationRunner {

    private final TestDataMapper mapper;
    private final TestDataAssetStore assets;
    private final TestDataStorageProperties properties;
    private final Clock clock;

    public TestDataRecovery(
            TestDataMapper mapper, TestDataAssetStore assets, TestDataStorageProperties properties, Clock clock) {
        this.mapper = mapper;
        this.assets = assets;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(properties.staleAge()), ZoneOffset.UTC);
        mapper.failStaleUploads(cutoff, "UPLOAD_INTERRUPTED");
        assets.recover(new HashSet<>(mapper.listReadyStorageRefs()));
    }
}
