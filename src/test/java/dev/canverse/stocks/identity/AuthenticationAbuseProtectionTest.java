package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.identity.application.AuthenticationAbuseProtection;
import dev.canverse.stocks.identity.configuration.AuthenticationAbuseProtectionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AuthenticationAbuseProtectionTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    static class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant start) {
            this.current = start;
        }

        void set(Instant next) {
            this.current = next;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Test
    void loginPrincipalFailuresBlockAfterConfiguredThreshold() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        3, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var email = "user@example.com";
        var source = "192.168.1.100";

        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Failure 1
        assertThat(limiter.recordLoginFailure(email, source)).isEmpty();
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Failure 2
        assertThat(limiter.recordLoginFailure(email, source)).isEmpty();
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Failure 3 - reaches threshold (3), should transition to blocked!
        assertThat(limiter.recordLoginFailure(email, source)).isPresent();
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // Subsequent check remains blocked
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // Unrelated email on same source is not blocked by principal threshold (source threshold is 10)
        assertThat(limiter.checkLoginAllowed("other@example.com", source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Advance clock to block expiry (exact equality is unblocked)
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
    }

    @Test
    void loginSourceFailuresBlockAcrossRotatingEmails() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 3, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var source = "10.0.0.1";

        // 3 different emails on same source
        assertThat(limiter.recordLoginFailure("email1@example.com", source)).isEmpty();
        assertThat(limiter.recordLoginFailure("email2@example.com", source)).isEmpty();
        // 3rd failure reaches source threshold (3)
        assertThat(limiter.recordLoginFailure("email3@example.com", source)).isPresent();

        // Now all emails on that source are blocked
        assertThat(limiter.checkLoginAllowed("brand-new@example.com", source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
    }

    @Test
    void loginSuccessResetsPrincipalBucketOnly() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        3, 5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var email = "user@example.com";
        var source = "192.168.1.1";

        // 2 failures for email
        limiter.recordLoginFailure(email, source);
        limiter.recordLoginFailure(email, source);

        // Login succeeds
        limiter.recordLoginSuccess(email, source);

        // Email can now fail 2 more times without triggering 3-failure threshold
        assertThat(limiter.recordLoginFailure(email, source)).isEmpty();
        assertThat(limiter.recordLoginFailure(email, source)).isEmpty();
        // But source bucket has now accumulated 2 (before) + 2 (after) + 1 (next) = 5 -> reaches source limit!
        assertThat(limiter.recordLoginFailure(email, source)).isPresent();
    }

    @Test
    void registrationConsumesAttemptsAndBlocksBeyondMax() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                null,
                new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        3, Duration.ofHours(1), Duration.ofHours(1)),
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var source = "192.168.1.50";

        // Attempts 1, 2, 3 are allowed
        assertThat(limiter.consumeRegistrationAttempt(source).status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.ALLOWED);
        assertThat(limiter.consumeRegistrationAttempt(source).status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.ALLOWED);
        assertThat(limiter.consumeRegistrationAttempt(source).status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.ALLOWED);

        // Attempt 4 is beyond max (3) -> enters blocked state
        var result4 = limiter.consumeRegistrationAttempt(source);
        assertThat(result4.status()).isEqualTo(AuthenticationAbuseProtection.AttemptStatus.JUST_BLOCKED);
        assertThat(result4.transition()).isNotNull();

        // Attempt 5 while blocked -> BLOCKED
        assertThat(limiter.consumeRegistrationAttempt(source).status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.BLOCKED);

        // Advancing clock by 1 hour unblocks
        clock.advance(Duration.ofHours(1));
        assertThat(limiter.consumeRegistrationAttempt(source).status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.ALLOWED);
    }

    @Test
    void refreshFailuresBlockAndSuccessResets() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                null,
                null,
                new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        2, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var source = "172.16.0.1";

        assertThat(limiter.checkRefreshAllowed(source)).isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Failure 1
        assertThat(limiter.recordRefreshFailure(source)).isEmpty();

        // Refresh succeeds -> resets bucket
        limiter.recordRefreshSuccess(source);

        // Can fail once more without blocking
        assertThat(limiter.recordRefreshFailure(source)).isEmpty();
        // Second failure reaches limit (2)
        assertThat(limiter.recordRefreshFailure(source)).isPresent();
        assertThat(limiter.checkRefreshAllowed(source)).isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
    }

    @Test
    void capacityPruningRemovesExpiredEntries() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        2, 2, Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        2, Duration.ofMinutes(10), Duration.ofMinutes(10)),
                new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        2, Duration.ofMinutes(10), Duration.ofMinutes(10)),
                2); // max 2 tracked keys
        var limiter = new AuthenticationAbuseProtection(props, clock);

        limiter.recordLoginFailure("u1@example.com", "1.1.1.1");
        limiter.recordLoginFailure("u2@example.com", "2.2.2.2");

        // Advance clock past window
        clock.advance(Duration.ofMinutes(15));

        // Adding a new key triggers pruning of expired entries
        limiter.recordLoginFailure("u3@example.com", "3.3.3.3");

        assertThat(limiter.checkLoginAllowed("u3@example.com", "3.3.3.3"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
    }

    @Test
    void concurrentLoginAttemptsAreThreadSafe() throws Exception {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        100, 100, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var email = "concurrent@example.com";
        var source = "10.0.0.99";

        var threads = 8;
        var iterationsPerThread = 20;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        limiter.recordLoginFailure(email, source);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 8 * 20 = 160 total attempts > 100 max failures -> must be BLOCKED
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
    }

    @Test
    void nullAndBlankSourceNormalizesToUnknown() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        10, 2, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                1000);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        limiter.recordLoginFailure("user1@example.com", null);
        limiter.recordLoginFailure("user2@example.com", "   ");

        // Both normalized to unknown, so source limit (2) is reached
        assertThat(limiter.checkLoginAllowed("user3@example.com", ""))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
    }

    @Test
    void exhaustedCapacityFailsClosedForUntrackedKeysAndRecoversAfterPruning() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        30, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                2);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        // Fill capacity with 2 entries
        limiter.recordLoginFailure("user1@example.com", "10.0.0.1"); // creates principal + source keys (2 keys)

        // Untracked keys beyond capacity must fail closed
        assertThat(limiter.checkLoginAllowed("untracked@example.com", "10.0.0.2"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
        assertThat(limiter.checkRefreshAllowed("10.0.0.2"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
        assertThat(limiter.consumeRegistrationAttempt("10.0.0.2").status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.BLOCKED);
        assertThat(limiter.recordLoginFailure("untracked@example.com", "10.0.0.2"))
                .isEmpty();

        // Advance clock past the window duration so existing keys become stale
        clock.advance(Duration.ofHours(2));

        // Capacity pruning on access removes stale keys and allows new untracked keys
        assertThat(limiter.checkLoginAllowed("untracked@example.com", "10.0.0.2"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
        assertThat(limiter.checkRefreshAllowed("10.0.0.2"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
        assertThat(limiter.consumeRegistrationAttempt("10.0.0.2").status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.ALLOWED);
    }

    @Test
    void mixedWindowConfigurationDoesNotPruneLongerWindowsEarly() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        3, 10, Duration.ofHours(2), Duration.ofHours(2)),
                new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        5, Duration.ofMinutes(5), Duration.ofMinutes(5)),
                new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        5, Duration.ofHours(3), Duration.ofHours(3)),
                2);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        // Record 2 failures on login (fills capacity with 2 keys: principal and source)
        limiter.recordLoginFailure("loginuser@example.com", "192.168.1.1");
        limiter.recordLoginFailure("loginuser@example.com", "192.168.1.1");

        // Advance 30 minutes: past registration window (5m), but within login window (2h)
        clock.advance(Duration.ofMinutes(30));

        // Capacity check should NOT prune active login buckets
        assertThat(limiter.checkLoginAllowed("other@example.com", "10.0.0.99"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 3rd failure on existing user reaches principal limit of 3 -> blocks
        var justBlocked = limiter.recordLoginFailure("loginuser@example.com", "192.168.1.1");
        assertThat(justBlocked).isPresent();
        assertThat(limiter.checkLoginAllowed("loginuser@example.com", "192.168.1.1"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);
    }

    @Test
    void throttleRollbackRestoresAllowedState() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        2, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        2, Duration.ofHours(1), Duration.ofHours(1)),
                new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        2, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                100);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        // Trigger login block
        limiter.recordLoginFailure("test@example.com", "1.1.1.1");
        var loginTransition =
                limiter.recordLoginFailure("test@example.com", "1.1.1.1").orElseThrow();
        assertThat(limiter.checkLoginAllowed("test@example.com", "1.1.1.1"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // Rollback login throttle using transition handle
        limiter.rollbackThrottle(loginTransition);
        assertThat(limiter.checkLoginAllowed("test@example.com", "1.1.1.1"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // Trigger registration block
        limiter.consumeRegistrationAttempt("2.2.2.2");
        limiter.consumeRegistrationAttempt("2.2.2.2");
        var regResult = limiter.consumeRegistrationAttempt("2.2.2.2");
        assertThat(regResult.status()).isEqualTo(AuthenticationAbuseProtection.AttemptStatus.JUST_BLOCKED);
        assertThat(regResult.transition()).isNotNull();

        // Rollback registration throttle using transition handle
        limiter.rollbackThrottle(regResult.transition());
        assertThat(limiter.consumeRegistrationAttempt("2.2.2.2").status())
                .isEqualTo(AuthenticationAbuseProtection.AttemptStatus.JUST_BLOCKED);
    }

    @Test
    void supersededThrottleRollbackDoesNotClearNewerBlock() {
        var clock = new MutableClock(T0);
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        2, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                100);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        // 1. Establish Block 1 (transitions at T0 -> blockedUntil = T0 + 15m)
        limiter.recordLoginFailure("user@example.com", "1.2.3.4");
        var transition1 =
                limiter.recordLoginFailure("user@example.com", "1.2.3.4").orElseThrow();
        assertThat(limiter.checkLoginAllowed("user@example.com", "1.2.3.4"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 2. Block 1 expires after 20 minutes
        clock.advance(Duration.ofMinutes(20));
        assertThat(limiter.checkLoginAllowed("user@example.com", "1.2.3.4"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // 3. New activity establishes Block 2 (transitions at T0 + 20m -> blockedUntil = T0 + 35m)
        limiter.recordLoginFailure("user@example.com", "1.2.3.4");
        var transition2 =
                limiter.recordLoginFailure("user@example.com", "1.2.3.4").orElseThrow();
        assertThat(limiter.checkLoginAllowed("user@example.com", "1.2.3.4"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 4. Stalled event write from earlier Block 1 finally fails and calls rollback with transition1
        limiter.rollbackThrottle(transition1);

        // Compare-and-set CAS mismatch: transition1 version != current version (Block 2)
        // Block 2 must remain ACTIVE and NOT cleared!
        assertThat(limiter.checkLoginAllowed("user@example.com", "1.2.3.4"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 5. When Block 2's event write fails and calls rollback with transition2, it clears Block 2
        limiter.rollbackThrottle(transition2);
        assertThat(limiter.checkLoginAllowed("user@example.com", "1.2.3.4"))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
    }

    @Test
    void sameClockReplacementThrottleRollbackDoesNotClearRecreatedBlock() {
        var clock = new MutableClock(T0); // Clock stays fixed at T0
        var props = new AuthenticationAbuseProtectionProperties(
                new AuthenticationAbuseProtectionProperties.LoginProperties(
                        1, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                null,
                null,
                100);
        var limiter = new AuthenticationAbuseProtection(props, clock);

        var email = "user@example.com";
        var source = "1.2.3.4";

        // 1. Establish Block 1 at T0 (threshold is 1, so 1 failure triggers block at T0 -> blockedUntil = T0 + 15m)
        var transition1 = limiter.recordLoginFailure(email, source).orElseThrow();
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 2. State is reset at the EXACT SAME clock instant T0 (e.g. success or reset)
        limiter.recordLoginSuccess(email, source);
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);

        // 3. New failure at the EXACT SAME clock instant T0 establishes Block 2 with IDENTICAL blockedUntil (T0 + 15m)
        var transition2 = limiter.recordLoginFailure(email, source).orElseThrow();
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 4. Stalled event write from earlier Block 1 fails and calls rollback with transition1 (version V1)
        limiter.rollbackThrottle(transition1);

        // Block 2 must remain ACTIVE because monotonic blockVersion V2 != V1 (even though blockedUntil is identical)
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.BLOCKED);

        // 5. When Block 2's event write fails and calls rollback with transition2 (version V2), it clears Block 2
        limiter.rollbackThrottle(transition2);
        assertThat(limiter.checkLoginAllowed(email, source))
                .isEqualTo(AuthenticationAbuseProtection.CheckResult.ALLOWED);
    }
}
