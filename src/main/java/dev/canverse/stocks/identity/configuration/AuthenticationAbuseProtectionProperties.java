package dev.canverse.stocks.identity.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocks.identity.abuse-protection")
public record AuthenticationAbuseProtectionProperties(
        LoginProperties login, RegistrationProperties registration, RefreshProperties refresh, Integer maxTrackedKeys) {

    public static final int DEFAULT_MAX_TRACKED_KEYS = 10000;

    public AuthenticationAbuseProtectionProperties {
        if (login == null) {
            login = new LoginProperties(null, null, null, null);
        }
        if (registration == null) {
            registration = new RegistrationProperties(null, null, null);
        }
        if (refresh == null) {
            refresh = new RefreshProperties(null, null, null);
        }
        if (maxTrackedKeys == null) {
            maxTrackedKeys = DEFAULT_MAX_TRACKED_KEYS;
        } else if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("maxTrackedKeys must be positive");
        }
    }

    public record LoginProperties(
            Integer principalMaxFailures, Integer sourceMaxFailures, Duration window, Duration blockDuration) {

        public static final int DEFAULT_PRINCIPAL_MAX_FAILURES = 5;
        public static final int DEFAULT_SOURCE_MAX_FAILURES = 25;
        public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
        public static final Duration DEFAULT_BLOCK_DURATION = Duration.ofMinutes(15);

        public LoginProperties {
            if (principalMaxFailures == null) {
                principalMaxFailures = DEFAULT_PRINCIPAL_MAX_FAILURES;
            } else if (principalMaxFailures <= 0) {
                throw new IllegalArgumentException("principalMaxFailures must be positive");
            }
            if (sourceMaxFailures == null) {
                sourceMaxFailures = DEFAULT_SOURCE_MAX_FAILURES;
            } else if (sourceMaxFailures <= 0) {
                throw new IllegalArgumentException("sourceMaxFailures must be positive");
            }
            if (window == null) {
                window = DEFAULT_WINDOW;
            } else if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("window must be positive");
            }
            if (blockDuration == null) {
                blockDuration = DEFAULT_BLOCK_DURATION;
            } else if (blockDuration.isNegative() || blockDuration.isZero()) {
                throw new IllegalArgumentException("blockDuration must be positive");
            }
        }
    }

    public record RegistrationProperties(Integer sourceMaxAttempts, Duration window, Duration blockDuration) {

        public static final int DEFAULT_SOURCE_MAX_ATTEMPTS = 10;
        public static final Duration DEFAULT_WINDOW = Duration.ofHours(1);
        public static final Duration DEFAULT_BLOCK_DURATION = Duration.ofHours(1);

        public RegistrationProperties {
            if (sourceMaxAttempts == null) {
                sourceMaxAttempts = DEFAULT_SOURCE_MAX_ATTEMPTS;
            } else if (sourceMaxAttempts <= 0) {
                throw new IllegalArgumentException("sourceMaxAttempts must be positive");
            }
            if (window == null) {
                window = DEFAULT_WINDOW;
            } else if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("window must be positive");
            }
            if (blockDuration == null) {
                blockDuration = DEFAULT_BLOCK_DURATION;
            } else if (blockDuration.isNegative() || blockDuration.isZero()) {
                throw new IllegalArgumentException("blockDuration must be positive");
            }
        }
    }

    public record RefreshProperties(Integer sourceMaxFailures, Duration window, Duration blockDuration) {

        public static final int DEFAULT_SOURCE_MAX_FAILURES = 30;
        public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
        public static final Duration DEFAULT_BLOCK_DURATION = Duration.ofMinutes(15);

        public RefreshProperties {
            if (sourceMaxFailures == null) {
                sourceMaxFailures = DEFAULT_SOURCE_MAX_FAILURES;
            } else if (sourceMaxFailures <= 0) {
                throw new IllegalArgumentException("sourceMaxFailures must be positive");
            }
            if (window == null) {
                window = DEFAULT_WINDOW;
            } else if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("window must be positive");
            }
            if (blockDuration == null) {
                blockDuration = DEFAULT_BLOCK_DURATION;
            } else if (blockDuration.isNegative() || blockDuration.isZero()) {
                throw new IllegalArgumentException("blockDuration must be positive");
            }
        }
    }
}
