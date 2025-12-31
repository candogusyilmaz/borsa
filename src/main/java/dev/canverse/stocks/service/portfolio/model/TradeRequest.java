package dev.canverse.stocks.service.portfolio.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeRequest(
        @NotNull
        Long stockId,
        @NotNull
        String currencyCode,
        @NotNull
        @Positive
        BigDecimal quantity,
        @NotNull
        @Positive
        BigDecimal price,
        @PositiveOrZero
        BigDecimal commission,
        @NotNull
        Instant actionDate,
        String notes,
        List<String> tags
) {
}
