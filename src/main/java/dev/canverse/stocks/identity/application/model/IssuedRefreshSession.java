package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record IssuedRefreshSession(UUID sessionId, UUID familyId, String refreshToken, Instant expiresAt) {}
