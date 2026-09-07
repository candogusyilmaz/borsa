package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.identity.application.DeviceSessionRevocationService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"stocks.identity.refresh-session.lifetime=30d", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@Testcontainers
@Import(RefreshRevocationConcurrencyTest.TestOverrides.class)
class RefreshRevocationConcurrencyTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService issuanceService;

    @Autowired
    RefreshSessionRotationService rotationService;

    @Autowired
    DeviceSessionRevocationService revocationService;

    @Autowired
    DeviceSessionRepository sessionRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
    }

    @Test
    void revocationWinsWhenAcquiringOwnerLockFirst() throws Exception {
        var userId = registrationService.register("revokefirst@example.com", "correct horse battery staple");
        var session = issuanceService.issue(userId, "laptop");

        var revocationLocked = new CountDownLatch(1);
        var releaseRevocation = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var revocationFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                revocationService.logoutCurrentSession(userId, session.sessionId());
                revocationLocked.countDown();
                await(releaseRevocation);
                return null;
            }));

            assertThat(revocationLocked.await(10, TimeUnit.SECONDS)).isTrue();

            // Refresh starts while revocation transaction holds the owner lock
            var refreshFuture = executor.submit(() -> rotationService.rotate(session.refreshToken()));
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRevocation.countDown();
            revocationFuture.get(10, TimeUnit.SECONDS);
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);

            // Refresh fails because family was already revoked
            assertThat(refreshResult).isEmpty();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session.sessionId())).isEmpty();
        } finally {
            releaseRevocation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void revocationAfterRefreshRevokesTheSuccessorGeneration() throws Exception {
        var userId = registrationService.register("refreshfirst@example.com", "correct horse battery staple");
        var session = issuanceService.issue(userId, "laptop");

        var refreshLocked = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var refreshFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = rotationService.rotate(session.refreshToken());
                refreshLocked.countDown();
                await(releaseRefresh);
                return result;
            }));

            assertThat(refreshLocked.await(10, TimeUnit.SECONDS)).isTrue();

            // Revocation starts while refresh holds the owner lock
            var revocationFuture = executor.submit(() -> {
                revocationService.logoutCurrentSession(userId, session.sessionId());
                return null;
            });
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRefresh.countDown();
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);
            revocationFuture.get(10, TimeUnit.SECONDS);

            assertThat(refreshResult).isPresent();
            // Revocation executed after refresh reloaded the terminal generation and
            // revoked the successor
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session.sessionId())).isEmpty();
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void selectedFamilyRevocationWinsWhenAcquiringOwnerLockFirst() throws Exception {
        var userId = registrationService.register("selectedfirst@example.com", "correct horse battery staple");
        var session = issuanceService.issue(userId, "laptop");

        var revocationLocked = new CountDownLatch(1);
        var releaseRevocation = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var revocationFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                revocationService.revokeSelectedFamily(userId, session.sessionId(), session.sessionId());
                revocationLocked.countDown();
                await(releaseRevocation);
                return null;
            }));

            assertThat(revocationLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var refreshFuture = executor.submit(() -> rotationService.rotate(session.refreshToken()));
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRevocation.countDown();
            revocationFuture.get(10, TimeUnit.SECONDS);
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);

            assertThat(refreshResult).isEmpty();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session.sessionId())).isEmpty();
        } finally {
            releaseRevocation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void selectedFamilyRevocationAfterRefreshRevokesTheSuccessorGeneration() throws Exception {
        var userId = registrationService.register("refreshfirstsel@example.com", "correct horse battery staple");
        var session = issuanceService.issue(userId, "laptop");

        var refreshLocked = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var refreshFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = rotationService.rotate(session.refreshToken());
                refreshLocked.countDown();
                await(releaseRefresh);
                return result;
            }));

            assertThat(refreshLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var revocationFuture = executor.submit(() -> {
                revocationService.revokeSelectedFamily(userId, session.sessionId(), session.sessionId());
                return null;
            });
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRefresh.countDown();
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);
            revocationFuture.get(10, TimeUnit.SECONDS);

            assertThat(refreshResult).isPresent();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session.sessionId())).isEmpty();
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void allSessionLogoutWinsWhenAcquiringOwnerLockFirst() throws Exception {
        var userId = registrationService.register("allfirst@example.com", "correct horse battery staple");
        var session1 = issuanceService.issue(userId, "laptop");
        var session2 = issuanceService.issue(userId, "phone");

        var revocationLocked = new CountDownLatch(1);
        var releaseRevocation = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var logoutFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                revocationService.logoutAllSessions(userId);
                revocationLocked.countDown();
                await(releaseRevocation);
                return null;
            }));

            assertThat(revocationLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var refreshFuture = executor.submit(() -> rotationService.rotate(session1.refreshToken()));
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRevocation.countDown();
            logoutFuture.get(10, TimeUnit.SECONDS);
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);

            assertThat(refreshResult).isEmpty();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session1.sessionId())).isEmpty();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session2.sessionId())).isEmpty();
        } finally {
            releaseRevocation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void allSessionLogoutAfterRefreshRevokesTheSuccessorGeneration() throws Exception {
        var userId = registrationService.register("refreshfirstall@example.com", "correct horse battery staple");
        var session1 = issuanceService.issue(userId, "laptop");
        var session2 = issuanceService.issue(userId, "phone");

        var refreshLocked = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var refreshFuture = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = rotationService.rotate(session1.refreshToken());
                refreshLocked.countDown();
                await(releaseRefresh);
                return result;
            }));

            assertThat(refreshLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var logoutFuture = executor.submit(() -> {
                revocationService.logoutAllSessions(userId);
                return null;
            });
            assertThat(waitingOnUserAccountLock()).isTrue();

            releaseRefresh.countDown();
            var refreshResult = refreshFuture.get(10, TimeUnit.SECONDS);
            logoutFuture.get(10, TimeUnit.SECONDS);

            assertThat(refreshResult).isPresent();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session1.sessionId())).isEmpty();
            assertThat(sessionRepository.findByFamilyIdAndRevokedAtIsNull(session2.sessionId())).isEmpty();
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    private boolean waitingOnUserAccountLock() {
        for (var attempt = 0; attempt < 500; attempt++) {
            var waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity a " + "WHERE a.wait_event_type = 'Lock' " +
                    "AND cardinality(pg_blocking_pids(a.pid)) > 0 " + "AND a.query ILIKE '%user_account%'", Long.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted concurrent test", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }
    }
}
