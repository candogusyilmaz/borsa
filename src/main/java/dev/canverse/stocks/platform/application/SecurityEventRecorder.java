package dev.canverse.stocks.platform.application;

import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.platform.domain.SecurityEvent;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.platform.infrastructure.SecurityEventRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SecurityEventRecorder {

    public static final String LOCAL_LOGIN_SUCCEEDED = "LOCAL_LOGIN_SUCCEEDED";
    public static final String LOCAL_LOGIN_FAILED = "LOCAL_LOGIN_FAILED";
    public static final String LOCAL_LOGIN_THROTTLED = "LOCAL_LOGIN_THROTTLED";
    public static final String REGISTRATION_THROTTLED = "REGISTRATION_THROTTLED";
    public static final String REFRESH_REUSE_DETECTED = "REFRESH_REUSE_DETECTED";
    public static final String REFRESH_THROTTLED = "REFRESH_THROTTLED";
    public static final String CURRENT_SESSION_LOGGED_OUT = "CURRENT_SESSION_LOGGED_OUT";
    public static final String ALL_SESSIONS_LOGGED_OUT = "ALL_SESSIONS_LOGGED_OUT";
    public static final String DEVICE_SESSION_REVOKED = "DEVICE_SESSION_REVOKED";

    public static final Set<String> ANONYMOUS_EVENT_TYPES =
            Set.of(LOCAL_LOGIN_FAILED, LOCAL_LOGIN_THROTTLED, REGISTRATION_THROTTLED, REFRESH_THROTTLED);

    public static final Set<String> USER_SCOPED_EVENT_TYPES = Set.of(
            LOCAL_LOGIN_SUCCEEDED,
            REFRESH_REUSE_DETECTED,
            CURRENT_SESSION_LOGGED_OUT,
            ALL_SESSIONS_LOGGED_OUT,
            DEVICE_SESSION_REVOKED);

    private final SecurityEventRepository securityEventRepository;
    private final EntityManager entityManager;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID userAccountId, String eventType, Map<String, Object> details) {
        recordInternal(userAccountId, eventType, details, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAt(UUID userAccountId, String eventType, Map<String, Object> details, Instant occurredAt) {
        recordInternal(userAccountId, eventType, details, occurredAt);
    }

    private void recordInternal(UUID userAccountId, String eventType, Map<String, Object> details, Instant occurredAt) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(occurredAt, "occurredAt");
        var immutableDetails = immutableDetails(details);
        validateDetailShape(eventType, immutableDetails, false);

        var userAccountProxy = entityManager.getReference(UserAccount.class, userAccountId);
        var jsonDetails = serializeDetails(immutableDetails);
        var event = SecurityEvent.create(idGenerator.next(), userAccountProxy, eventType, occurredAt, jsonDetails);
        securityEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymousRequiresNew(String eventType, Map<String, Object> details) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(details, "details");
        var immutableDetails = immutableDetails(details);
        validateDetailShape(eventType, immutableDetails, true);

        var jsonDetails = serializeDetails(immutableDetails);
        var event = SecurityEvent.create(idGenerator.next(), null, eventType, clock.instant(), jsonDetails);
        securityEventRepository.saveAndFlush(event);
    }

    private void validateDetailShape(String eventType, Map<String, Object> details, boolean anonymous) {
        if (anonymous && !ANONYMOUS_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Event type " + eventType + " is not an anonymous event type");
        }
        if (!anonymous && !USER_SCOPED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("Event type " + eventType + " is not a user-scoped event type");
        }

        Set<String> requiredKeys =
                switch (eventType) {
                    case LOCAL_LOGIN_SUCCEEDED -> {
                        validateCanonicalUuidString(details, "sessionId");
                        validateCanonicalUuidString(details, "familyId");
                        yield Set.of("sessionId", "familyId");
                    }
                    case LOCAL_LOGIN_FAILED, LOCAL_LOGIN_THROTTLED -> {
                        validateNonBlankString(details, "traceId");
                        if (!"LOGIN".equals(details.get("operation"))) {
                            throw new IllegalArgumentException("operation must be LOGIN");
                        }
                        yield Set.of("traceId", "operation");
                    }
                    case REGISTRATION_THROTTLED -> {
                        validateNonBlankString(details, "traceId");
                        if (!"REGISTER".equals(details.get("operation"))) {
                            throw new IllegalArgumentException("operation must be REGISTER");
                        }
                        yield Set.of("traceId", "operation");
                    }
                    case REFRESH_REUSE_DETECTED -> {
                        validateCanonicalUuidString(details, "familyId");
                        validateCanonicalUuidString(details, "sessionId");
                        yield Set.of("familyId", "sessionId");
                    }
                    case REFRESH_THROTTLED -> {
                        validateNonBlankString(details, "traceId");
                        if (!"REFRESH".equals(details.get("operation"))) {
                            throw new IllegalArgumentException("operation must be REFRESH");
                        }
                        yield Set.of("traceId", "operation");
                    }
                    case CURRENT_SESSION_LOGGED_OUT, DEVICE_SESSION_REVOKED -> {
                        validateCanonicalUuidString(details, "familyId");
                        yield Set.of("familyId");
                    }
                    case ALL_SESSIONS_LOGGED_OUT -> {
                        if (!(details.get("revokedFamilyCount") instanceof Byte
                                        || details.get("revokedFamilyCount") instanceof Short
                                        || details.get("revokedFamilyCount") instanceof Integer
                                        || details.get("revokedFamilyCount") instanceof Long)
                                || ((Number) details.get("revokedFamilyCount")).longValue() < 0) {
                            throw new IllegalArgumentException("revokedFamilyCount must be a non-negative number");
                        }
                        yield Set.of("revokedFamilyCount");
                    }
                    default -> throw new IllegalArgumentException("Unknown security event type: " + eventType);
                };

        if (!details.keySet().equals(requiredKeys)) {
            throw new IllegalArgumentException("Invalid details keys for event type " + eventType + ": expected "
                    + requiredKeys + ", actual " + details.keySet());
        }
    }

    private void validateNonBlankString(Map<String, Object> details, String key) {
        var value = details.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Detail value for key " + key + " must be a non-blank string");
        }
    }

    private void validateCanonicalUuidString(Map<String, Object> details, String key) {
        validateNonBlankString(details, key);
        var value = (String) details.get(key);
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("Detail value for key " + key + " must be a canonical UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Detail value for key " + key + " must be a canonical UUID", exception);
        }
    }

    private Map<String, Object> immutableDetails(Map<String, Object> details) {
        return Map.copyOf(details);
    }

    private String serializeDetails(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize security event details to JSON", exception);
        }
    }
}
