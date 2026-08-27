package com.cherryoj.userservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cherryoj.userservice.application.AuditService;
import com.cherryoj.userservice.application.AuthenticationService;
import com.cherryoj.userservice.application.UserAdministrationService;
import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.IdentityConflictException;
import com.cherryoj.userservice.domain.UsernamePolicy;
import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.security.LoginGrantCodec;
import com.cherryoj.userservice.security.PasswordService;
import com.cherryoj.userservice.security.TokenService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"cherry.auth.mode=test", "spring.main.web-application-type=none"})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserPersistenceIntegrationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_user")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    UserAdministrationService users;

    @Autowired
    UserAccountMapper accounts;

    @Autowired
    LoginSessionMapper sessions;

    @Autowired
    UsernamePolicy usernames;

    @Autowired
    PasswordService passwords;

    @Autowired
    LoginGrantCodec grants;

    @Autowired
    AuditService audit;

    @Autowired
    UuidV7 uuidV7;

    @Autowired
    AuthProperties properties;

    @Autowired
    Clock clock;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @Order(1)
    void flywayAndMappersPreserveAccountInvariants() {
        var admin = users.bootstrapAdmin("admin01", "Admin-Initial-7!Secret");
        var created = users.createUser(admin.id(), "Learner01");

        assertThat(created.user().role().name()).isEqualTo("USER");
        assertThat(created.user().passwordChangeRequired()).isTrue();
        assertThat(created.temporaryPassword()).hasSize(24);
        assertThat(accounts.findById(created.user().id()).passwordHash()).startsWith("{argon2}");
        assertThat(users.listUsers(1, 20).totalElements()).isEqualTo(2);

        var disabled = users.updateStatus(
                admin.id(), created.user().id(), UserStatus.DISABLED, created.user().rowVersion());
        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        var reset = users.resetPassword(admin.id(), created.user().id(), disabled.rowVersion());
        assertThat(reset.user().passwordChangeRequired()).isTrue();
        assertThat(reset.user().rowVersion()).isGreaterThan(disabled.rowVersion());

        assertThatThrownBy(() -> users.createUser(admin.id(), "learner01"))
                .isInstanceOf(IdentityConflictException.class);
        assertThatThrownBy(() -> users.bootstrapAdmin("admin02", "Another-Admin-7!Secret"))
                .isInstanceOf(IdentityConflictException.class);
    }

    @Test
    @Order(2)
    void failedLoginBackoffSurvivesTheGenericAuthenticationException() {
        var created = users.createUser(null, "LockedLearner");
        var authentication = new AuthenticationService(
                accounts,
                sessions,
                usernames,
                passwords,
                grants,
                mock(TokenService.class),
                audit,
                uuidV7,
                properties,
                clock,
                transactionManager);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authentication.authenticate("LockedLearner", "Wrong-Password-42!"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        var locked = accounts.findById(created.user().id());
        assertThat(locked.failedLoginCount()).isEqualTo(5);
        assertThat(locked.lockedUntil()).isNotNull();
    }

    @Test
    @Order(3)
    void mysqlTouchConditionallyRefreshesIdleDeadline() {
        var created = users.createUser(null, "SessionLearner");
        String rawGrant = grants.generate();
        byte[] digest = grants.digest(rawGrant);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
        LocalDateTime initialIdle = now.plusSeconds(600);
        LocalDateTime absolute = now.plusSeconds(3_600);
        sessions.insert(
                uuidV7.next().toString(), created.user().id(), digest, 0,
                now, initialIdle, absolute);

        var initial = sessions.findActiveByGrantHashForUpdate(digest, now.plusSeconds(1));
        LocalDateTime refreshedIdle = now.plusSeconds(1_801);
        assertThat(sessions.touch(
                initial.sessionId(), now.plusSeconds(1), refreshedIdle, true, initial.rowVersion()))
                .isEqualTo(1);
        var refreshed = sessions.findActiveByGrantHashForUpdate(digest, now.plusSeconds(2));
        assertThat(refreshed.idleExpiresAt()).isEqualTo(refreshedIdle);

        assertThat(sessions.touch(
                refreshed.sessionId(), now.plusSeconds(2), now.plusSeconds(2_000), false,
                refreshed.rowVersion())).isEqualTo(1);
        var fixed = sessions.findActiveByGrantHashForUpdate(digest, now.plusSeconds(3));
        assertThat(fixed.idleExpiresAt()).isEqualTo(refreshedIdle);
        assertThat(fixed.absoluteExpiresAt()).isEqualTo(absolute);
    }
}
