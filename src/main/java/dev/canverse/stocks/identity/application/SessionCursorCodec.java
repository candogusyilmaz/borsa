package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class SessionCursorCodec {

    private static final String VERSION_PREFIX = "v1";

    private SessionCursorCodec() {}

    public static String encode(Instant createdAt, UUID familyId) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(familyId, "familyId");

        var payload = VERSION_PREFIX
                + "|"
                + createdAt.getEpochSecond()
                + "|"
                + createdAt.getNano()
                + "|"
                + familyId.toString().toLowerCase(Locale.ROOT);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static SessionCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw invalidCursor();
        }

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getUrlDecoder().decode(cursor);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }

        var decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
        var parts = decodedString.split("\\|", -1);
        if (parts.length != 4 || !VERSION_PREFIX.equals(parts[0])) {
            throw invalidCursor();
        }

        long epochSecond;
        int nano;
        UUID familyId;

        try {
            epochSecond = Long.parseLong(parts[1]);
            if (!parts[1].equals(Long.toString(epochSecond))) {
                throw invalidCursor();
            }

            nano = Integer.parseInt(parts[2]);
            if (!parts[2].equals(Integer.toString(nano)) || nano < 0 || nano >= 1_000_000_000) {
                throw invalidCursor();
            }

            familyId = UUID.fromString(parts[3]);
            if (!parts[3].equals(familyId.toString().toLowerCase(Locale.ROOT))) {
                throw invalidCursor();
            }
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }

        try {
            var createdAt = Instant.ofEpochSecond(epochSecond, nano);
            // Canonical check: re-encoding must produce the identical input cursor string
            var reEncoded = encode(createdAt, familyId);
            if (!reEncoded.equals(cursor)) {
                throw invalidCursor();
            }
            return new SessionCursor(createdAt, familyId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private static AppException invalidCursor() {
        return new AppException(IdentityErrorCode.INVALID_SESSION_CURSOR);
    }
}
