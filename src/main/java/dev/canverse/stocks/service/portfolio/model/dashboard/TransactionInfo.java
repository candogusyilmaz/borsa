package dev.canverse.stocks.service.portfolio.model.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionInfo(
        String id,
        Transaction.Type type,
        String portfolioId,
        String positionId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal profit,
        Instant actionDate) {
}
