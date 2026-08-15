package dev.canverse.stocks.identity.output;

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

    public DeviceSessionResponse {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(latestGenerationId, "latestGenerationId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
    }
}
