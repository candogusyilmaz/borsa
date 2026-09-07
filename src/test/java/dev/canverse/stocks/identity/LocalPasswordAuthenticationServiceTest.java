package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.LocalPasswordAuthenticationService;
import dev.canverse.stocks.identity.domain.AuthIdentity;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LocalPasswordAuthenticationServiceTest {

    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String WRONG_PASSWORD = "incorrect horse battery staple";
    private static final Instant DISABLED_AT = Instant.parse("2026-08-08T13:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    LocalPasswordAuthenticationService authenticationService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearIdentityTables() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
    }

    @Test
    void validLocalCredentialsReturnUserIdWithoutChangingPersistedIdentityState() {
        var registeredUserId = registrationService.register("Alice.Example@Example.COM", RAW_PASSWORD);
        var beforeAuthentication = snapshot();

        var authenticatedUserId = authenticationService.authenticate("ALICE.EXAMPLE@EXAMPLE.COM", RAW_PASSWORD);

        assertThat(authenticatedUserId).isEqualTo(registeredUserId);
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    @Test
    void wrongPasswordAndUnknownEmailUseTheSameSafeFailureWithoutWrites() {
        registrationService.register("alice@example.com", RAW_PASSWORD);
        var beforeAuthentication = snapshot();

        var wrongPassword = catchThrowable(() -> authenticationService.authenticate("alice@example.com", WRONG_PASSWORD));
        var unknownEmail = catchThrowable(() -> authenticationService.authenticate("unknown@example.com", RAW_PASSWORD));

        assertCredentialFailure(wrongPassword, "alice@example.com", WRONG_PASSWORD);
        assertCredentialFailure(unknownEmail, "unknown@example.com", RAW_PASSWORD);
        assertThat(((AppException) wrongPassword).getMessage()).isEqualTo(((AppException) unknownEmail).getMessage());
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    @Test
    void disabledAccountFailsClosedWithoutChangingPersistedIdentityState() {
        var userId = registrationService.register("disabled@example.com", RAW_PASSWORD);
        runInTransaction(
                () -> jdbcTemplate.update("UPDATE identity.user_account SET disabled_at = ? WHERE id = ?", DISABLED_AT.atOffset(ZoneOffset.UTC), userId));
        var beforeAuthentication = snapshot();

        var thrown = catchThrowable(() -> authenticationService.authenticate("disabled@example.com", RAW_PASSWORD));

        assertCredentialFailure(thrown, "disabled@example.com", RAW_PASSWORD);
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    @Test
    void nullLocalPasswordHashFailsClosedWithoutChangingPersistedIdentityState() {
        registrationService.register("unusable@example.com", RAW_PASSWORD);
        runInTransaction(() -> jdbcTemplate.update(
                "UPDATE identity.auth_identity SET password_hash = NULL WHERE provider = 'LOCAL'" + " AND provider_subject = ?", "unusable@example.com"));
        var beforeAuthentication = snapshot();

        var thrown = catchThrowable(() -> authenticationService.authenticate("unusable@example.com", RAW_PASSWORD));

        assertCredentialFailure(thrown, "unusable@example.com", RAW_PASSWORD);
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    private void assertCredentialFailure(Throwable thrown, String email, String rawPassword) {
        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(exception.toString()).doesNotContain(email, rawPassword);
    }

    private PersistedIdentityState snapshot() {
        var users = userAccountRepository.findAll();
        var identities = authIdentityRepository.findAll();
        UserAccount user = users.isEmpty() ? null : users.getFirst();
        AuthIdentity identity = identities.isEmpty() ? null : identities.getFirst();
        return new PersistedIdentityState(users.size(), identities.size(), user == null ? null : user.getId(), user == null ? null : user.getEmail(),
                user == null ? null : user.getEmailNormalized(), user == null ? null : user.getDisabledAt(), user == null ? null : user.getCreatedAt(),
                user == null ? null : user.getUpdatedAt(), identity == null ? null : identity.getId(), identity == null ? null : identity.getProvider(),
                identity == null ? null : identity.getProviderSubject(), identity == null ? null : identity.getPasswordHash(),
                identity == null ? null : identity.getCreatedAt(), identity == null ? null : identity.getUpdatedAt());
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private record PersistedIdentityState(int userCount, int identityCount, UUID userId, String email, String emailNormalized, Instant disabledAt,
            Instant userCreatedAt, Instant userUpdatedAt, UUID identityId, String provider, String providerSubject, String passwordHash,
            Instant identityCreatedAt, Instant identityUpdatedAt) {}
}
