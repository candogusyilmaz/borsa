package dev.canverse.stocks.service.stock.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record BuyTradeRequest(
        @NotNull
        Long stockId,
        @NotNull
        @Positive
        int quantity,
        @NotNull
        @Positive
        BigDecimal price,
        @PositiveOrZero
        BigDecimal tax
) {
}
