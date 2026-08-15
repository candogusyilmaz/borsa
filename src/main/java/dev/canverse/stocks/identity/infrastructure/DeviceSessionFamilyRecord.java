package dev.canverse.stocks.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record DeviceSessionFamilyRecord(
        UUID familyId,
        UUID latestGenerationId,
        String deviceLabel,
        Instant createdAt,
        Instant lastUsedAt,
        Instant minExpiresAt,
        Instant maxExpiresAt,
        Instant terminalRevokedAt,
        String terminalRevokeReason,
        boolean current) {}
