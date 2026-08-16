package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.testing.RecordingIdGenerator;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(LocalAccountRegistrationServiceTest.TestOverrides.class)
class LocalAccountRegistrationServiceTest {

    private static final Instant REGISTRATION_TIME = Instant.parse("2026-08-08T12:34:56Z");
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.setNextIds();
    }

    @Test
    void registrationCreatesUserAndLocalIdentityAtomically() {
        var userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var authIdentityId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        idGenerator.setNextIds(userId, authIdentityId);

        var returnedId = registrationService.register("Alice.Example@Example.COM", RAW_PASSWORD);

        assertThat(returnedId).isEqualTo(userId);
        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();

        var userAccount = userAccountRepository.findById(userId).orElseThrow();
        var authIdentity = authIdentityRepository.findById(authIdentityId).orElseThrow();

        assertThat(userAccount.getId()).isEqualTo(returnedId);
        assertThat(userAccount.getEmail()).isEqualTo("Alice.Example@Example.COM");
        assertThat(userAccount.getEmailNormalized()).isEqualTo("alice.example@example.com");
        assertThat(userAccount.getCreatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(userAccount.getUpdatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(authIdentity.getId()).isNotEqualTo(returnedId);
        assertThat(authIdentity.getProvider()).isEqualTo("LOCAL");
        assertThat(authIdentity.getProviderSubject()).isEqualTo("alice.example@example.com");
        assertThat(authIdentity.getCreatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(authIdentity.getUpdatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(authIdentity.getPasswordHash()).isNotEqualTo(RAW_PASSWORD).startsWith("{");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, authIdentity.getPasswordHash()))
                .isTrue();
        assertThat(passwordEncoder.matches("wrong password", authIdentity.getPasswordHash()))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT user_account_id FROM identity.auth_identity WHERE id = ?", UUID.class, authIdentityId))
                .isEqualTo(userId);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        var userId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        var authIdentityId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        idGenerator.setNextIds(userId, authIdentityId);

        registrationService.register("Alice.Example@Example.COM", RAW_PASSWORD);
        var firstUserAccount = userAccountRepository.findById(userId).orElseThrow();
        var firstAuthIdentity = authIdentityRepository.findById(authIdentityId).orElseThrow();

        var thrown = catchThrowable(() -> registrationService.register("alice.example@example.com", RAW_PASSWORD));

        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        assertThat(((AppException) thrown).getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        assertThat(thrown).hasMessage("The email address is already registered.");
        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();
        var persistedUserAccount = userAccountRepository.findById(userId).orElseThrow();
        var persistedAuthIdentity =
                authIdentityRepository.findById(authIdentityId).orElseThrow();
        assertThat(persistedUserAccount.getId()).isEqualTo(firstUserAccount.getId());
        assertThat(persistedUserAccount.getEmail()).isEqualTo(firstUserAccount.getEmail());
        assertThat(persistedUserAccount.getEmailNormalized()).isEqualTo(firstUserAccount.getEmailNormalized());
        assertThat(persistedUserAccount.getCreatedAt()).isEqualTo(firstUserAccount.getCreatedAt());
        assertThat(persistedUserAccount.getUpdatedAt()).isEqualTo(firstUserAccount.getUpdatedAt());
        assertThat(persistedAuthIdentity.getId()).isEqualTo(firstAuthIdentity.getId());
        assertThat(persistedAuthIdentity.getProvider()).isEqualTo(firstAuthIdentity.getProvider());
        assertThat(persistedAuthIdentity.getProviderSubject()).isEqualTo(firstAuthIdentity.getProviderSubject());
        assertThat(persistedAuthIdentity.getPasswordHash()).isEqualTo(firstAuthIdentity.getPasswordHash());
        assertThat(persistedAuthIdentity.getCreatedAt()).isEqualTo(firstAuthIdentity.getCreatedAt());
        assertThat(persistedAuthIdentity.getUpdatedAt()).isEqualTo(firstAuthIdentity.getUpdatedAt());
    }

    @Test
    void authIdentityConstraintFailureRollsBackNewUser() {
        var fixtureUserId = UUID.fromString("50000000-0000-0000-0000-000000000005");
        var fixtureAuthIdentityId = UUID.fromString("60000000-0000-0000-0000-000000000006");
        var requestedUserId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        var requestedAuthIdentityId = UUID.fromString("80000000-0000-0000-0000-000000000008");

        runInTransaction(() -> {
            jdbcTemplate.update(
                    "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    fixtureUserId,
                    "fixture-owner@example.com",
                    "fixture-owner@example.com",
                    REGISTRATION_TIME.atOffset(ZoneOffset.UTC),
                    REGISTRATION_TIME.atOffset(ZoneOffset.UTC));
            jdbcTemplate.update(
                    "INSERT INTO identity.auth_identity"
                            + " (id, user_account_id, provider, provider_subject, password_hash, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    fixtureAuthIdentityId,
                    fixtureUserId,
                    "LOCAL",
                    "collision@example.com",
                    "fixture-hash",
                    REGISTRATION_TIME.atOffset(ZoneOffset.UTC),
                    REGISTRATION_TIME.atOffset(ZoneOffset.UTC));
        });
        idGenerator.setNextIds(requestedUserId, requestedAuthIdentityId);

        var thrown = catchThrowable(() -> registrationService.register("Collision@example.com", RAW_PASSWORD));

        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        assertThat(((AppException) thrown).getErrorCode()).isEqualTo(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();
        assertThat(userAccountRepository.findById(fixtureUserId).orElseThrow().getEmail())
                .isEqualTo("fixture-owner@example.com");
        assertThat(authIdentityRepository
                        .findById(fixtureAuthIdentityId)
                        .orElseThrow()
                        .getProviderSubject())
                .isEqualTo("collision@example.com");
        assertThat(userAccountRepository.existsByEmailNormalized("collision@example.com"))
                .isFalse();
    }

    @Test
    void invalidRegistrationInputWritesNothing() {
        assertValidationFailure("not-an-email", RAW_PASSWORD);
        assertValidationFailure(" alice@example.com", RAW_PASSWORD);
        assertValidationFailure("alice@example.com ", RAW_PASSWORD);
        assertValidationFailure("alice@example.com", " ");
        assertValidationFailure("alice@example.com", "short");
        assertValidationFailure("alice@example.com", "p".repeat(129));
        assertValidationFailure("a".repeat(310) + "@example.com", RAW_PASSWORD);

        assertThat(userAccountRepository.count()).isZero();
        assertThat(authIdentityRepository.count()).isZero();
    }

    private void assertValidationFailure(String email, String rawPassword) {
        assertThatThrownBy(() -> registrationService.register(email, rawPassword))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(userAccountRepository.count()).isZero();
        assertThat(authIdentityRepository.count()).isZero();
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(REGISTRATION_TIME, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }
    }
}
