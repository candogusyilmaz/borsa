package dev.canverse.stocks.identity.web.request;

import jakarta.validation.constraints.NotNull;

public record LocalRefreshRequest(
        String refreshToken, @NotNull RefreshTokenDelivery refreshTokenDelivery) {}
