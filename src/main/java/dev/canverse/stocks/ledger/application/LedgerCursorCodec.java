package dev.canverse.stocks.ledger.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.canverse.stocks.ledger.application.model.AccountCursor;
import dev.canverse.stocks.ledger.application.model.ActivityCursor;
import dev.canverse.stocks.ledger.application.model.ReconciliationCursor;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.application.CursorTokenCodec;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Canonical JSON/Base64url cursors whose filter digest prevents cross-query reuse. */
@Component
@RequiredArgsConstructor
public class LedgerCursorCodec {

    private static final int VERSION = 1;

    private record AccountCursorPayload(
            @JsonProperty("v") int version,
            @JsonProperty("f") String filterDigest,
            @JsonProperty("n") String name,
            @JsonProperty("i") String accountId) {}

    private record ActivityCursorPayload(
            @JsonProperty("v") int version,
            @JsonProperty("f") String filterDigest,
            @JsonProperty("t") String recordedAt,
            @JsonProperty("i") String activityId) {}

    private record ReconciliationCursorPayload(
            @JsonProperty("v") int version,
            @JsonProperty("f") String filterDigest,
            @JsonProperty("t") String statementClosingAt,
            @JsonProperty("i") String reconciliationId) {}

    private final CursorTokenCodec tokenCodec;
    private final CanonicalFingerprint fingerprint;
    private final ObjectMapper objectMapper;

    public String encodeAccount(AccountCursor cursor) {
        return encodePayload(new AccountCursorPayload(
                VERSION,
                cursor.filterDigest(),
                cursor.nameNormalized(),
                cursor.accountId().toString()));
    }

    public AccountCursor decodeAccount(String encoded) {
        return decodeAccount(encoded, null);
    }

    public AccountCursor decodeAccount(String encoded, String expectedFilterDigest) {
        var payload = decodePayload(encoded, AccountCursorPayload.class);
        validateVersionAndFilter(payload.version(), payload.filterDigest());
        verifyFilter(payload.filterDigest(), expectedFilterDigest);
        return new AccountCursor(payload.filterDigest(), required(payload.name()), canonicalUuid(payload.accountId()));
    }

    public String encodeActivity(ActivityCursor cursor) {
        return encodePayload(new ActivityCursorPayload(
                VERSION,
                cursor.filterDigest(),
                cursor.recordedAt().toString(),
                cursor.activityId().toString()));
    }

    public ActivityCursor decodeActivity(String encoded) {
        return decodeActivity(encoded, null);
    }

    public ActivityCursor decodeActivity(String encoded, String expectedFilterDigest) {
        var payload = decodePayload(encoded, ActivityCursorPayload.class);
        validateVersionAndFilter(payload.version(), payload.filterDigest());
        verifyFilter(payload.filterDigest(), expectedFilterDigest);
        return new ActivityCursor(
                payload.filterDigest(), canonicalInstant(payload.recordedAt()), canonicalUuid(payload.activityId()));
    }

    public String encodeReconciliation(ReconciliationCursor cursor) {
        return encodePayload(new ReconciliationCursorPayload(
                VERSION,
                cursor.filterDigest(),
                cursor.statementClosingAt().toString(),
                cursor.reconciliationId().toString()));
    }

    public ReconciliationCursor decodeReconciliation(String encoded) {
        return decodeReconciliation(encoded, null);
    }

    public ReconciliationCursor decodeReconciliation(String encoded, String expectedFilterDigest) {
        var payload = decodePayload(encoded, ReconciliationCursorPayload.class);
        validateVersionAndFilter(payload.version(), payload.filterDigest());
        verifyFilter(payload.filterDigest(), expectedFilterDigest);
        return new ReconciliationCursor(
                payload.filterDigest(),
                canonicalInstant(payload.statementClosingAt()),
                canonicalUuid(payload.reconciliationId()));
    }

    public String reconciliationFilterDigest(UUID accountId) {
        return digest("accountId\n" + accountId);
    }

    public String accountFilterDigest(boolean includeArchived) {
        return digest("includeArchived\n" + includeArchived);
    }

    public String activityFilterDigest(UUID accountId) {
        return digest("accountId\n" + (accountId == null ? "" : accountId));
    }

    private <T> T decodePayload(String encoded, Class<T> payloadType) {
        final String json;
        final T payload;
        try {
            json = tokenCodec.decode(encoded);
            payload = objectMapper.readValue(json, payloadType);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalidCursor();
        }
        if (payload == null) {
            throw invalidCursor();
        }
        if (!encoded.equals(encodePayload(payload))) {
            throw invalidCursor();
        }
        return payload;
    }

    private String encodePayload(Object payload) {
        try {
            return tokenCodec.encode(objectMapper.writeValueAsString(payload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize cursor payload", exception);
        }
    }

    private static void validateVersionAndFilter(int version, String filterDigest) {
        if (version != VERSION || !isCanonicalDigest(filterDigest)) {
            throw invalidCursor();
        }
    }

    private static boolean isCanonicalDigest(String value) {
        return value != null
                && value.length() == 64
                && value.chars()
                        .allMatch(character ->
                                character >= '0' && character <= '9' || character >= 'a' && character <= 'f');
    }

    private static UUID canonicalUuid(String value) {
        if (value == null) {
            throw invalidCursor();
        }
        final UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
        if (!uuid.toString().equals(value)) {
            throw invalidCursor();
        }
        return uuid;
    }

    private static Instant canonicalInstant(String value) {
        if (value == null) {
            throw invalidCursor();
        }
        final Instant instant;
        try {
            instant = Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidCursor();
        }
        if (!instant.toString().equals(value)) {
            throw invalidCursor();
        }
        return instant;
    }

    private static String required(String value) {
        if (value == null) {
            throw invalidCursor();
        }
        return value;
    }

    private static void verifyFilter(String supplied, String expected) {
        if (expected != null
                && !MessageDigest.isEqual(
                        supplied.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII))) {
            throw invalidCursor();
        }
    }

    private String digest(String value) {
        return fingerprint.hashText(value);
    }

    private static AppException invalidCursor() {
        return ValidationErrors.invalidField("cursor", "error.fields.ledger.invalid_cursor", "The cursor is invalid.");
    }
}
