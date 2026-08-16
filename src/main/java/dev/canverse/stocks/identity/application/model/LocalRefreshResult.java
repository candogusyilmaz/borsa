package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record LocalRefreshResult(
        UUID sessionId,
        String accessToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String refreshToken) {}
