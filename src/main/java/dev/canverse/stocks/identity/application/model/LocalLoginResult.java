package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record LocalLoginResult(UUID sessionId, String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {}
