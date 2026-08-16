package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LocalRefreshResult(
        UUID sessionId,
        String accessToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String refreshToken) {

    public LocalRefreshResult {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}
