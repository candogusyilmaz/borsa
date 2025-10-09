package dev.canverse.stocks.service.portfolio.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        @NotNull
        Long stockId,
        @NotNull
        @Positive
        BigDecimal quantity,
        @NotNull
        @Positive
        BigDecimal price,
        @PositiveOrZero
        BigDecimal commission,
        @NotNull
        Instant actionDate
) {
}
