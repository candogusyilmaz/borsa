package dev.canverse.stocks.reference.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.application.CursorTokenCodec;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCursor;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InstrumentSearchCursorCodec {

    private static final int VERSION = 1;

    private record InstrumentCursorPayload(
            @JsonProperty("v") int version,
            @JsonProperty("f") String filterDigest,
            @JsonProperty("s") String symbol,
            @JsonProperty("m") String marketCode,
            @JsonProperty("i") String instrumentId) {}

    private final CursorTokenCodec tokenCodec;
    private final CanonicalFingerprint fingerprint;
    private final ObjectMapper objectMapper;

    public String encode(InstrumentSearchCursor cursor) {
        return encodePayload(new InstrumentCursorPayload(
                VERSION,
                cursor.filterDigest(),
                cursor.symbolNormalized(),
                cursor.marketCodeNormalized(),
                cursor.instrumentId().toString()));
    }

    public InstrumentSearchCursor decode(String encoded, String expectedFilterDigest) {
        var payload = decodePayload(encoded);
        validatePayload(payload);
        if (expectedFilterDigest == null
                || !MessageDigest.isEqual(
                        expectedFilterDigest.getBytes(StandardCharsets.US_ASCII),
                        payload.filterDigest().getBytes(StandardCharsets.US_ASCII))) {
            throw invalidCursor();
        }
        return new InstrumentSearchCursor(
                payload.filterDigest(), payload.symbol(), payload.marketCode(), canonicalUuid(payload.instrumentId()));
    }

    public String filterDigest(String queryNormalized, UUID marketId, InstrumentType type, boolean includeInactive) {
        var filter = "%s\n%s\n%s\n%s"
                .formatted(
                        queryNormalized == null ? "" : queryNormalized,
                        marketId == null ? "" : marketId,
                        type == null ? "" : type.name(),
                        includeInactive);
        return fingerprint.hashText(filter);
    }

    private InstrumentCursorPayload decodePayload(String encoded) {
        final String json;
        final InstrumentCursorPayload payload;
        try {
            json = tokenCodec.decode(encoded);
            payload = objectMapper.readValue(json, InstrumentCursorPayload.class);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalidCursor();
        }
        if (payload == null || !encoded.equals(encodePayload(payload))) {
            throw invalidCursor();
        }
        return payload;
    }

    private String encodePayload(InstrumentCursorPayload payload) {
        try {
            return tokenCodec.encode(objectMapper.writeValueAsString(payload));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize instrument cursor payload", exception);
        }
    }

    private static void validatePayload(InstrumentCursorPayload payload) {
        if (payload.version() != VERSION
                || !isCanonicalDigest(payload.filterDigest())
                || !isCanonicalCode(payload.symbol(), true)
                || !isCanonicalCode(payload.marketCode(), false)) {
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

    private static boolean isCanonicalCode(String value, boolean symbol) {
        if (value == null || value.isEmpty() || value.length() > 32 || !isAlphaNumeric(value.charAt(0))) {
            return false;
        }
        for (var index = 1; index < value.length(); index++) {
            var character = value.charAt(index);
            if (!isAlphaNumeric(character)
                    && (symbol ? ".:/+-".indexOf(character) < 0 : ".-_".indexOf(character) < 0)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlphaNumeric(char character) {
        return character >= 'A' && character <= 'Z' || character >= '0' && character <= '9';
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

    private static AppException invalidCursor() {
        return new AppException(ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR);
    }
}
