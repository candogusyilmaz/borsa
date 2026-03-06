package dev.canverse.stocks.service.portfolio.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.domain.entity.portfolio.TransactionPerformance;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeHistory(@NotNull List<Item> trades) {
    public record Item(
            @NotNull
            Instant date,
            @NotNull
            Instant createdAt,
            @NotNull
            Transaction.Type type,
            @NotNull
            String holdingId,
            @NotNull
            String symbol,
            @NotNull
            BigDecimal price,
            @NotNull
            BigDecimal quantity,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal profit,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal returnPercentage,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            TransactionPerformance.PerformanceCategory performanceCategory,
            @NotNull
            boolean latest
    ) {
        @NotNull
        public BigDecimal getTotal() {
            return price.multiply(quantity);
        }
    }
}
