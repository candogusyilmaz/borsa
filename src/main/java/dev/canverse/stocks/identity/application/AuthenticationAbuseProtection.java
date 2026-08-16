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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAbuseProtection {

    private static final String LOGIN_PRINCIPAL_PREFIX = "LOGIN_PRINCIPAL\0";
    private static final String LOGIN_SOURCE_PREFIX = "LOGIN\0";
    private static final String REGISTER_SOURCE_PREFIX = "REGISTER\0";
    private static final String REFRESH_SOURCE_PREFIX = "REFRESH\0";
    private static final String UNKNOWN_SOURCE = "unknown";

    private final AuthenticationAbuseProtectionProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, BucketState> trackedKeys = new ConcurrentHashMap<>();
    private final AtomicLong versionSequence = new AtomicLong(0);
    private final ReentrantLock admissionLock = new ReentrantLock();

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

    public CheckResult checkLoginAllowed(String email, String source) {
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

    public Optional<ThrottleTransition> recordLoginFailure(String email, String source) {
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

    public void recordLoginSuccess(String email, String source) {
        var principalKey = computeLoginPrincipalKey(email, source);
        trackedKeys.remove(principalKey);
    }

    public RegistrationAttemptResult consumeRegistrationAttempt(String source) {
        var observedAt = clock.instant();
        var sourceKey = computeSourceKey(REGISTER_SOURCE_PREFIX, source);
        var regProps = properties.registration();

        if (isBlocked(sourceKey, observedAt) || isCapacityExhausted(sourceKey, observedAt)) {
            return new RegistrationAttemptResult(AttemptStatus.BLOCKED, null);
        }

        var result = tryConsumeRegistrationAttempt(sourceKey, observedAt, regProps);
        if (result != null) {
            return result;
        }

        admissionLock.lock();
        try {
            pruneStale(observedAt);
            if (!trackedKeys.containsKey(sourceKey) && trackedKeys.size() >= properties.maxTrackedKeys()) {
                return new RegistrationAttemptResult(AttemptStatus.BLOCKED, null);
            }
            return consumeRegistrationAttemptUnderAdmission(sourceKey, observedAt, regProps);
        } finally {
            admissionLock.unlock();
        }
    }

    public CheckResult checkRefreshAllowed(String source) {
        var observedAt = clock.instant();
        var sourceKey = computeSourceKey(REFRESH_SOURCE_PREFIX, source);

        if (isBlocked(sourceKey, observedAt) || isCapacityExhausted(sourceKey, observedAt)) {
            return CheckResult.BLOCKED;
        }
        return CheckResult.ALLOWED;
    }

    public Optional<ThrottleTransition> recordRefreshFailure(String source) {
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

    public void recordRefreshSuccess(String source) {
        var sourceKey = computeSourceKey(REFRESH_SOURCE_PREFIX, source);
        trackedKeys.remove(sourceKey);
    }

    public void rollbackThrottle(ThrottleTransition transition) {
        if (transition == null) {
            return;
        }
        for (var entry : transition.expectedVersions().entrySet()) {
            var key = entry.getKey();
            var expectedVersion = entry.getValue();
            trackedKeys.computeIfPresent(
                    key,
                    (ignored, current) -> current.blockVersion() == expectedVersion
                            ? new BucketState(current.windowStart(), current.count(), null, current.window(), 0L)
                            : current);
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
        var existing = trackedKeys.get(key);
        if (existing != null && !isExpired(existing, observedAt)) {
            var retryAdmission = new AtomicBoolean();
            var newlyBlockedVersion = new AtomicReference<Long>();
            trackedKeys.compute(key, (ignored, current) -> {
                if (current == null || isExpired(current, observedAt)) {
                    retryAdmission.set(true);
                    return current;
                }
                return applyFailure(current, observedAt, maxFailures, window, blockDuration, newlyBlockedVersion);
            });
            if (!retryAdmission.get()) {
                return newlyBlockedVersion.get();
            }
        }

        admissionLock.lock();
        try {
            pruneStale(observedAt);
            var newlyBlockedVersion = new AtomicReference<Long>();
            var rejected = new AtomicBoolean();
            trackedKeys.compute(key, (ignored, current) -> {
                if (current == null) {
                    if (trackedKeys.size() >= properties.maxTrackedKeys()) {
                        rejected.set(true);
                        return null;
                    }
                    return initialFailure(observedAt, maxFailures, window, blockDuration, newlyBlockedVersion);
                }
                if (isExpired(current, observedAt)) {
                    return initialFailure(observedAt, maxFailures, window, blockDuration, newlyBlockedVersion);
                }
                return applyFailure(current, observedAt, maxFailures, window, blockDuration, newlyBlockedVersion);
            });
            return rejected.get() ? null : newlyBlockedVersion.get();
        } finally {
            admissionLock.unlock();
        }
    }

    private BucketState initialFailure(
            Instant observedAt,
            int maxFailures,
            Duration window,
            Duration blockDuration,
            AtomicReference<Long> newlyBlockedVersion) {
        Instant blockedUntil = null;
        long blockVersion = 0L;
        if (maxFailures <= 1) {
            blockedUntil = observedAt.plus(blockDuration);
            blockVersion = versionSequence.incrementAndGet();
            newlyBlockedVersion.set(blockVersion);
        }
        return new BucketState(observedAt, 1, blockedUntil, window, blockVersion);
    }

    private BucketState applyFailure(
            BucketState existing,
            Instant observedAt,
            int maxFailures,
            Duration window,
            Duration blockDuration,
            AtomicReference<Long> newlyBlockedVersion) {
        if (existing.blockedUntil() != null && observedAt.isBefore(existing.blockedUntil())) {
            return existing;
        }

        var newCount = existing.count() + 1;
        var blockedUntil = existing.blockedUntil();
        var blockVersion = existing.blockVersion();
        if (newCount >= maxFailures && blockedUntil == null) {
            blockedUntil = observedAt.plus(blockDuration);
            blockVersion = versionSequence.incrementAndGet();
            newlyBlockedVersion.set(blockVersion);
        }
        return new BucketState(existing.windowStart(), newCount, blockedUntil, window, blockVersion);
    }

    private RegistrationAttemptResult tryConsumeRegistrationAttempt(
            String key, Instant observedAt, AuthenticationAbuseProtectionProperties.RegistrationProperties properties) {
        var existing = trackedKeys.get(key);
        if (existing == null || isExpired(existing, observedAt)) {
            return null;
        }

        var retryAdmission = new AtomicBoolean();
        var status = new AtomicReference<AttemptStatus>();
        var transition = new AtomicReference<ThrottleTransition>();
        trackedKeys.compute(key, (ignored, current) -> {
            if (current == null || isExpired(current, observedAt)) {
                retryAdmission.set(true);
                return current;
            }
            return applyRegistrationAttempt(key, current, observedAt, properties, status, transition);
        });
        return retryAdmission.get() ? null : new RegistrationAttemptResult(status.get(), transition.get());
    }

    private RegistrationAttemptResult consumeRegistrationAttemptUnderAdmission(
            String key, Instant observedAt, AuthenticationAbuseProtectionProperties.RegistrationProperties properties) {
        var status = new AtomicReference<AttemptStatus>();
        var transition = new AtomicReference<ThrottleTransition>();
        trackedKeys.compute(key, (ignored, current) -> {
            if (current == null || isExpired(current, observedAt)) {
                status.set(AttemptStatus.ALLOWED);
                return new BucketState(observedAt, 1, null, properties.window(), 0L);
            }
            return applyRegistrationAttempt(key, current, observedAt, properties, status, transition);
        });
        return new RegistrationAttemptResult(status.get(), transition.get());
    }

    private BucketState applyRegistrationAttempt(
            String key,
            BucketState current,
            Instant observedAt,
            AuthenticationAbuseProtectionProperties.RegistrationProperties properties,
            AtomicReference<AttemptStatus> status,
            AtomicReference<ThrottleTransition> transition) {
        if (current.blockedUntil() != null && observedAt.isBefore(current.blockedUntil())) {
            status.set(AttemptStatus.BLOCKED);
            return current;
        }
        var bucket = new BucketState(
                current.windowStart(),
                current.count() + 1,
                current.blockedUntil(),
                properties.window(),
                current.blockVersion());
        if (bucket.count() > properties.sourceMaxAttempts()) {
            var blockedUntil = observedAt.plus(properties.blockDuration());
            var version = versionSequence.incrementAndGet();
            status.set(AttemptStatus.JUST_BLOCKED);
            transition.set(new ThrottleTransition(Map.of(key, version)));
            return new BucketState(bucket.windowStart(), bucket.count(), blockedUntil, bucket.window(), version);
        }
        status.set(AttemptStatus.ALLOWED);
        return bucket;
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
            admissionLock.lock();
            try {
                if (trackedKeys.containsKey(key)) {
                    return false;
                }
                pruneStale(observedAt);
                return trackedKeys.size() >= properties.maxTrackedKeys();
            } finally {
                admissionLock.unlock();
            }
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
