package dev.canverse.stocks.reference.web.response;

import jakarta.validation.constraints.NotNull;

public record CurrencyResponse(
        @NotNull String code, @NotNull String name, @NotNull String symbol, int minorUnit, boolean active) {}
