package dev.canverse.stocks.service.stock.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Stocks(String exchange, List<Symbol> symbols) {
    public record Symbol(
            String id,
            String symbol,
            String name,
            BigDecimal last,
            BigDecimal dailyChange,
            BigDecimal dailyChangePercent,
            Instant lastUpdated
    ) {
    }
}
