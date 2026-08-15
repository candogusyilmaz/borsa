package dev.canverse.stocks.identity.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalRefreshResponse(
        @NotNull UUID sessionId,
        @NotNull String accessToken,
        @NotNull Instant accessTokenExpiresAt,
        @NotNull Instant refreshTokenExpiresAt,
        @NotNull Instant serverTime,
        String refreshToken) {

    public LocalRefreshResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
        Objects.requireNonNull(serverTime, "serverTime");
    }
}
