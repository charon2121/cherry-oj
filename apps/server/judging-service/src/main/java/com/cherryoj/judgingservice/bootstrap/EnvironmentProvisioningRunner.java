package com.cherryoj.judgingservice.bootstrap;

import com.cherryoj.judgingservice.config.JudgingProperties;
import com.cherryoj.judgingservice.domain.UuidV7;
import com.cherryoj.judgingservice.persistence.JudgingRepository;
import com.cherryoj.judgingservice.persistence.JudgingRepository.EnvironmentProvision;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "cherry.judging.provision", name = "enabled", havingValue = "true")
public final class EnvironmentProvisioningRunner implements ApplicationRunner {
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private final JudgingProperties properties;
    private final JudgingRepository repository;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public EnvironmentProvisioningRunner(JudgingProperties properties, JudgingRepository repository,
                                         UuidV7 ids, Clock clock, ObjectMapper json,
                                         PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments ignored) {
        JudgingProperties.Provision value = properties.provision();
        require(value.name(), "name");
        require(value.fingerprint(), "fingerprint");
        require(value.architecture(), "architecture");
        require(value.cpuModel(), "cpu-model");
        require(value.osVersion(), "os-version");
        require(value.kernelVersion(), "kernel-version");
        require(value.judgeVersion(), "judge-version");
        require(value.sandboxVersion(), "sandbox-version");
        require(value.configDigest(), "config-digest");
        require(value.endpointRef(), "endpoint-ref");
        require(value.toolchainVersion(), "toolchain-version");
        require(value.languageConfigDigest(), "language-config-digest");
        java.net.URI endpoint = java.net.URI.create(value.endpointRef());
        if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalStateException("invalid provision endpoint-ref");
        }
        if (!LANGUAGE.matcher(value.languageId()).matches()) throw new IllegalStateException("invalid provision language-id");
        String id = value.id() == null || value.id().isBlank() ? ids.next().toString() : value.id();
        java.util.UUID.fromString(id);
        transactions.executeWithoutResult(status -> {
            var existing = repository.findActive(true);
            if (existing != null && value.allowExistingIdentical()
                    && existing.id().equals(id)
                    && existing.name().equals(value.name())
                    && existing.fingerprint().equals(value.fingerprint())
                    && existing.endpointRef().equals(value.endpointRef())
                    && repository.languageEnabled(id, value.languageId())) {
                return;
            }
            if (existing != null) {
                throw new IllegalStateException("an ACTIVE judge environment already exists; provisioning never switches it");
            }
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            repository.provisionEnvironment(new EnvironmentProvision(id, value.name(), value.fingerprint(),
                    value.architecture(), value.cpuModel(), value.osVersion(), value.kernelVersion(),
                    value.judgeVersion(), value.sandboxVersion(), value.configDigest(), value.endpointRef(),
                    value.languageId(), value.toolchainVersion(), value.languageConfigDigest()), now);
            repository.insertAudit(ids.next().toString(), "ENVIRONMENT", id, null,
                    "ENVIRONMENT_PROVISIONED", null, json.writeValueAsString(Map.of(
                            "environmentId", id, "fingerprint", value.fingerprint(),
                            "languageId", value.languageId())), now);
        });
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("missing provision " + name);
    }
}
