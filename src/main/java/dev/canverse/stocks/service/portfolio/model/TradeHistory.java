package dev.canverse.stocks.service.portfolio.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.domain.entity.portfolio.TransactionPerformance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeHistory(List<Item> trades) {
    public record Item(
            Instant date,
            Instant createdAt,
            Transaction.Type type,
            String holdingId,
            String symbol,
            BigDecimal price,
            BigDecimal quantity,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal profit,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal returnPercentage,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            TransactionPerformance.PerformanceCategory performanceCategory,
            boolean latest
    ) {
        public BigDecimal getTotal() {
            return price.multiply(quantity);
        }
    }
}
