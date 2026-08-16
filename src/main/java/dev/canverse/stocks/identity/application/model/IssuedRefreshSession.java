package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IssuedRefreshSession(UUID sessionId, UUID familyId, String refreshToken, Instant expiresAt) {

    public IssuedRefreshSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
