package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.configuration.AuthenticationAbuseProtectionProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAbuseProtection {

    private static final String LOGIN_PRINCIPAL_PREFIX = "LOGIN_PRINCIPAL\0";
    private static final String LOGIN_SOURCE_PREFIX = "LOGIN_SOURCE\0";
    private static final String REGISTER_SOURCE_PREFIX = "REGISTER_SOURCE\0";
    private static final String REFRESH_SOURCE_PREFIX = "REFRESH_SOURCE\0";
    private static final String UNKNOWN_SOURCE = "unknown";

    private final AuthenticationAbuseProtectionProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, BucketState> trackedKeys = new ConcurrentHashMap<>();
    private final AtomicLong versionSequence = new AtomicLong(0);

    public AuthenticationAbuseProtection(AuthenticationAbuseProtectionProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public enum CheckResult {
        ALLOWED,
        BLOCKED
    }

    public enum AttemptStatus {
        ALLOWED,
        JUST_BLOCKED,
        BLOCKED
    }

    public record ThrottleTransition(Map<String, Long> expectedVersions) {
        public ThrottleTransition {
            expectedVersions = Map.copyOf(expectedVersions);
        }
    }

    public record RegistrationAttemptResult(AttemptStatus status, ThrottleTransition transition) {
        public RegistrationAttemptResult {
            Objects.requireNonNull(status, "status");
        }
    }

    public synchronized CheckResult checkLoginAllowed(String email, String source) {
        var observedAt = clock.instant();
        var principalKey = computeLoginPrincipalKey(email, source);
        var sourceKey = computeSourceKey(LOGIN_SOURCE_PREFIX, source);

        if (isBlocked(principalKey, observedAt)
                || isBlocked(sourceKey, observedAt)
                || isCapacityExhausted(principalKey, observedAt)
                || isCapacityExhausted(sourceKey, observedAt)) {
            return CheckResult.BLOCKED;
        }
        return CheckResult.ALLOWED;
    }

    public synchronized Optional<ThrottleTransition> recordLoginFailure(String email, String source) {
        var observedAt = clock.instant();
        var principalKey = computeLoginPrincipalKey(email, source);
        var sourceKey = computeSourceKey(LOGIN_SOURCE_PREFIX, source);

        var loginProps = properties.login();
        var principalVersion = recordFailure(
                principalKey,
                observedAt,
                loginProps.principalMaxFailures(),
                loginProps.window(),
                loginProps.blockDuration());

        var sourceVersion = recordFailure(
                sourceKey, observedAt, loginProps.sourceMaxFailures(), loginProps.window(), loginProps.blockDuration());

        if (principalVersion == null && sourceVersion == null) {
            return Optional.empty();
        }
        var expectedVersions = new HashMap<String, Long>();
        if (principalVersion != null) {
            expectedVersions.put(principalKey, principalVersion);
        }
        if (sourceVersion != null) {
            expectedVersions.put(sourceKey, sourceVersion);
        }
        return Optional.of(new ThrottleTransition(expectedVersions));
    }

    public synchronized void recordLoginSuccess(String email, String source) {
        var principalKey = computeLoginPrincipalKey(email, source);
        trackedKeys.remove(principalKey);
    }

    public synchronized RegistrationAttemptResult consumeRegistrationAttempt(String source) {
        var observedAt = clock.instant();
        var sourceKey = computeSourceKey(REGISTER_SOURCE_PREFIX, source);
        var regProps = properties.registration();

        if (isBlocked(sourceKey, observedAt) || isCapacityExhausted(sourceKey, observedAt)) {
            return new RegistrationAttemptResult(AttemptStatus.BLOCKED, null);
        }

        var bucket = trackedKeys.compute(sourceKey, (key, existing) -> {
            if (existing == null || isExpired(existing, observedAt)) {
                return new BucketState(observedAt, 1, null, regProps.window(), 0L);
            }
            return new BucketState(
                    existing.windowStart(),
                    existing.count() + 1,
                    existing.blockedUntil(),
                    regProps.window(),
                    existing.blockVersion());
        });

        if (bucket.count() > regProps.sourceMaxAttempts()) {
            if (bucket.blockedUntil() == null) {
                var blockedUntil = observedAt.plus(regProps.blockDuration());
                var version = versionSequence.incrementAndGet();
                trackedKeys.put(
                        sourceKey,
                        new BucketState(
                                bucket.windowStart(), bucket.count(), blockedUntil, regProps.window(), version));
                return new RegistrationAttemptResult(
                        AttemptStatus.JUST_BLOCKED, new ThrottleTransition(Map.of(sourceKey, version)));
            }
            return new RegistrationAttemptResult(AttemptStatus.BLOCKED, null);
        }

        return new RegistrationAttemptResult(AttemptStatus.ALLOWED, null);
    }

    public synchronized CheckResult checkRefreshAllowed(String source) {
        var observedAt = clock.instant();
        var sourceKey = computeSourceKey(REFRESH_SOURCE_PREFIX, source);

        if (isBlocked(sourceKey, observedAt) || isCapacityExhausted(sourceKey, observedAt)) {
            return CheckResult.BLOCKED;
        }
        return CheckResult.ALLOWED;
    }

    public synchronized Optional<ThrottleTransition> recordRefreshFailure(String source) {
        var observedAt = clock.instant();
        var sourceKey = computeSourceKey(REFRESH_SOURCE_PREFIX, source);
        var refreshProps = properties.refresh();

        var version = recordFailure(
                sourceKey,
                observedAt,
                refreshProps.sourceMaxFailures(),
                refreshProps.window(),
                refreshProps.blockDuration());

        if (version == null) {
            return Optional.empty();
        }
        return Optional.of(new ThrottleTransition(Map.of(sourceKey, version)));
    }

    public synchronized void recordRefreshSuccess(String source) {
        var sourceKey = computeSourceKey(REFRESH_SOURCE_PREFIX, source);
        trackedKeys.remove(sourceKey);
    }

    public synchronized void rollbackThrottle(ThrottleTransition transition) {
        if (transition == null) {
            return;
        }
        for (var entry : transition.expectedVersions().entrySet()) {
            var key = entry.getKey();
            var expectedVersion = entry.getValue();
            var current = trackedKeys.get(key);
            if (current != null && current.blockVersion() == expectedVersion) {
                trackedKeys.put(
                        key, new BucketState(current.windowStart(), current.count(), null, current.window(), 0L));
            }
        }
    }

    private boolean isBlocked(String key, Instant observedAt) {
        var state = trackedKeys.get(key);
        if (state == null) {
            return false;
        }
        if (state.blockedUntil() != null) {
            if (observedAt.isBefore(state.blockedUntil())) {
                return true;
            }
            // Block expired at or after equality
            trackedKeys.remove(key, state);
            return false;
        }
        if (!observedAt.isBefore(state.windowStart().plus(state.window()))) {
            trackedKeys.remove(key, state);
            return false;
        }
        return false;
    }

    private Long recordFailure(
            String key, Instant observedAt, int maxFailures, Duration window, Duration blockDuration) {
        if (isCapacityExhausted(key, observedAt)) {
            return null;
        }

        var existing = trackedKeys.get(key);
        if (existing == null || isExpired(existing, observedAt)) {
            var count = 1;
            Instant blockedUntil = null;
            long blockVersion = 0L;
            Long newlyBlockedVersion = null;
            if (count >= maxFailures) {
                blockedUntil = observedAt.plus(blockDuration);
                blockVersion = versionSequence.incrementAndGet();
                newlyBlockedVersion = blockVersion;
            }
            trackedKeys.put(key, new BucketState(observedAt, count, blockedUntil, window, blockVersion));
            return newlyBlockedVersion;
        }

        if (existing.blockedUntil() != null && observedAt.isBefore(existing.blockedUntil())) {
            return null;
        }

        var newCount = existing.count() + 1;
        var blockedUntil = existing.blockedUntil();
        var blockVersion = existing.blockVersion();
        Long newlyBlockedVersion = null;
        if (newCount >= maxFailures && blockedUntil == null) {
            blockedUntil = observedAt.plus(blockDuration);
            blockVersion = versionSequence.incrementAndGet();
            newlyBlockedVersion = blockVersion;
        }
        trackedKeys.put(key, new BucketState(existing.windowStart(), newCount, blockedUntil, window, blockVersion));
        return newlyBlockedVersion;
    }

    private boolean isExpired(BucketState state, Instant observedAt) {
        if (state.blockedUntil() != null) {
            return !observedAt.isBefore(state.blockedUntil());
        }
        return !observedAt.isBefore(state.windowStart().plus(state.window()));
    }

    private boolean isCapacityExhausted(String key, Instant observedAt) {
        if (trackedKeys.containsKey(key)) {
            return false;
        }
        if (trackedKeys.size() >= properties.maxTrackedKeys()) {
            pruneStale(observedAt);
            return trackedKeys.size() >= properties.maxTrackedKeys() && !trackedKeys.containsKey(key);
        }
        return false;
    }

    private void pruneStale(Instant observedAt) {
        trackedKeys.entrySet().removeIf(entry -> isExpired(entry.getValue(), observedAt));
    }

    private String computeSourceKey(String prefix, String source) {
        var normalizedSource = normalizeSource(source);
        return hash(prefix + normalizedSource);
    }

    private String computeLoginPrincipalKey(String email, String source) {
        var normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);
        var normalizedSource = normalizeSource(source);
        return hash(LOGIN_PRINCIPAL_PREFIX + normalizedEmail + "\0" + normalizedSource);
    }

    private String normalizeSource(String source) {
        return (source == null || source.isBlank()) ? UNKNOWN_SOURCE : source;
    }

    private String hash(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record BucketState(
            Instant windowStart, int count, Instant blockedUntil, Duration window, long blockVersion) {
        public BucketState {
            Objects.requireNonNull(windowStart, "windowStart");
            Objects.requireNonNull(window, "window");
        }
    }
}
