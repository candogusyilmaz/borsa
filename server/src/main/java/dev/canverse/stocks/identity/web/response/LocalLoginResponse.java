package dev.canverse.stocks.identity.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.identity.application.model.LocalLoginResult;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalLoginResponse(@NotNull UUID sessionId, @NotNull String accessToken, @NotNull Instant accessTokenExpiresAt,
        @NotNull Instant refreshTokenExpiresAt, @NotNull Instant serverTime, String refreshToken) {

    public LocalLoginResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
        Objects.requireNonNull(serverTime, "serverTime");
    }

    public static LocalLoginResponse from(LocalLoginResult result, Instant serverTime, String refreshToken) {
        return new LocalLoginResponse(result.sessionId(), result.accessToken(), result.accessTokenExpiresAt(), result.refreshTokenExpiresAt(), serverTime,
                refreshToken);
    }
}
