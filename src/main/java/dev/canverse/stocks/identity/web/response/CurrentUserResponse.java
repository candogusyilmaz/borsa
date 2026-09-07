package dev.canverse.stocks.identity.web.response;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CurrentUserResponse(@NotNull UUID id, @NotNull String email, @NotNull Instant createdAt) {

    public CurrentUserResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
