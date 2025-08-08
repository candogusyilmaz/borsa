package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record SellTransactionRequest(
        @NotNull
        Long stockId,
        Transaction.Type type,
        @NotNull
        @Positive
        int quantity,
        @NotNull
        @Positive
        BigDecimal price,
        @PositiveOrZero
        BigDecimal commission,
        @NotNull
        Instant actionDate
) {
}
