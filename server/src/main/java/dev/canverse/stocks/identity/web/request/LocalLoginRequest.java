package dev.canverse.stocks.identity.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LocalLoginRequest(@NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email,

        @NotBlank @Size(min = 12, max = 128) String password, @Size(max = 128) @Pattern(regexp = "\\S(?:.*\\S)?") String deviceLabel,
        @NotNull RefreshTokenDelivery refreshTokenDelivery) {}
