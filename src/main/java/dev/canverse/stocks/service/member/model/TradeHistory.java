package dev.canverse.stocks.service.member.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.domain.entity.Trade;
import dev.canverse.stocks.domain.entity.TradePerformance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeHistory(List<Item> trades) {
    public record Item(
            Instant date,
            Instant createdAt,
            Trade.Type type,
            String holdingId,
            String symbol,
            BigDecimal price,
            int quantity,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal profit,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal returnPercentage,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            TradePerformance.PerformanceCategory performanceCategory,
            boolean latest
    ) {
        public BigDecimal getTotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
