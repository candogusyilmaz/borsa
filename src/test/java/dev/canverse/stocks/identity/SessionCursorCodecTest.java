package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.SessionCursorCodec;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionCursorCodecTest {

    @Test
    void encodesAndDecodesRoundTripWithNanosecondPrecision() {
        var createdAt = Instant.parse("2026-08-15T12:34:56.789123456Z");
        var familyId = UUID.fromString("12345678-1234-4234-8234-123456789abc");

        var encoded = SessionCursorCodec.encode(createdAt, familyId);
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).doesNotContain("="); // Unpadded Base64url

        var decoded = SessionCursorCodec.decode(encoded);
        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.familyId()).isEqualTo(familyId);
    }

    @Test
    void rejectsNullOrBlankCursor() {
        assertInvalidCursor(null);
        assertInvalidCursor("");
        assertInvalidCursor("   ");
    }

    @Test
    void rejectsMalformedBase64() {
        assertInvalidCursor("not-valid-base64!!!");
    }

    @Test
    void rejectsUnknownVersion() {
        var raw = "v2|1786797296|789123456|12345678-1234-4234-8234-123456789abc";
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encoded);
    }

    @Test
    void rejectsWrongSegmentCount() {
        var raw = "v1|1786797296|789123456";
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encoded);

        var rawExtra = "v1|1786797296|789123456|12345678-1234-4234-8234-123456789abc|extra";
        var encodedExtra =
                Base64.getUrlEncoder().withoutPadding().encodeToString(rawExtra.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encodedExtra);
    }

    @Test
    void rejectsNonCanonicalNumberFormatting() {
        // Leading zeros in epochSecond
        var rawLeadingZeros = "v1|01786797296|789123456|12345678-1234-4234-8234-123456789abc";
        var encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawLeadingZeros.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encoded);

        // Negative nanos
        var rawNegativeNanos = "v1|1786797296|-100|12345678-1234-4234-8234-123456789abc";
        var encodedNeg = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawNegativeNanos.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encodedNeg);

        // Nanos >= 1,000,000,000
        var rawOverflowNanos = "v1|1786797296|1000000000|12345678-1234-4234-8234-123456789abc";
        var encodedOverflow = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawOverflowNanos.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encodedOverflow);
    }

    @Test
    void rejectsUppercaseUuid() {
        var rawUpperUuid = "v1|1786797296|789123456|12345678-1234-4234-8234-123456789ABC";
        var encoded =
                Base64.getUrlEncoder().withoutPadding().encodeToString(rawUpperUuid.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encoded);
    }

    @Test
    void rejectsInvalidUuidFormat() {
        var rawBadUuid = "v1|1786797296|789123456|invalid-uuid-format";
        var encoded =
                Base64.getUrlEncoder().withoutPadding().encodeToString(rawBadUuid.getBytes(StandardCharsets.UTF_8));
        assertInvalidCursor(encoded);
    }

    @Test
    void rejectsPaddedBase64() {
        var createdAt = Instant.parse("2026-08-15T12:34:56.789123456Z");
        var familyId = UUID.fromString("12345678-1234-4234-8234-123456789abc");
        var payload = "v1|" + createdAt.getEpochSecond() + "|" + createdAt.getNano() + "|" + familyId;
        var padded = Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        if (padded.endsWith("=")) {
            assertInvalidCursor(padded);
        }
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> SessionCursorCodec.decode(cursor))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_SESSION_CURSOR));
    }
}
