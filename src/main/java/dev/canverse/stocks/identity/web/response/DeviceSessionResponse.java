package dev.canverse.stocks.identity.web.response;

import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionFamilyRecord;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceSessionResponse(
        @NotNull UUID familyId,
        @NotNull UUID latestGenerationId,
        String deviceLabel,
        @NotNull Instant createdAt,
        Instant lastUsedAt,
        @NotNull Instant expiresAt,
        Instant endedAt,
        @NotNull DeviceSessionStatus status,
        boolean current) {

    public static DeviceSessionResponse from(DeviceSessionFamilyRecord record, Instant observedAt) {
        if (!Objects.equals(record.minExpiresAt(), record.maxExpiresAt())) {
            throw new IllegalStateException("Inconsistent family expiry detected for family " + record.familyId());
        }

        var expiresAt = record.minExpiresAt();
        DeviceSessionStatus status;
        Instant endedAt;

        if (record.terminalRevokedAt() != null) {
            status = DeviceSession.REUSE_DETECTED_REVOKE_REASON.equals(record.terminalRevokeReason())
                    ? DeviceSessionStatus.COMPROMISED
                    : DeviceSessionStatus.REVOKED;
            endedAt = record.terminalRevokedAt();
        } else if (expiresAt.isAfter(observedAt)) {
            status = DeviceSessionStatus.ACTIVE;
            endedAt = null;
        } else {
            status = DeviceSessionStatus.EXPIRED;
            endedAt = expiresAt;
        }

        return new DeviceSessionResponse(
                record.familyId(),
                record.latestGenerationId(),
                record.deviceLabel(),
                record.createdAt(),
                record.lastUsedAt(),
                expiresAt,
                endedAt,
                status,
                record.current());
    }
}
