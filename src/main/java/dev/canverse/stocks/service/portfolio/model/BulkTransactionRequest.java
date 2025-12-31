package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record BulkTransactionRequest(
        Transaction.Type type,
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
    public TradeRequest toTransactionRequest() {
        return new TradeRequest(
                stockId,
                "TRY",
                quantity,
                price,
                commission,
                actionDate,
                null,
                null
        );
    }
}
