package dev.canverse.stocks.identity.input;

import jakarta.validation.constraints.NotNull;

public record LocalRefreshRequest(
        String refreshToken, @NotNull RefreshTokenDelivery refreshTokenDelivery) {}
