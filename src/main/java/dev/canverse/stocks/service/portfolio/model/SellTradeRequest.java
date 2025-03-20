package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.entity.Trade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record SellTradeRequest(
        @NotNull
        Long stockId,
        Trade.Type type,
        @NotNull
        @Positive
        int quantity,
        @NotNull
        @Positive
        BigDecimal price,
        @PositiveOrZero
        BigDecimal tax,
        @NotNull
        Instant actionDate
) {
}
