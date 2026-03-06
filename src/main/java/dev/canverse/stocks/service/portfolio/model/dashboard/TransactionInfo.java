package dev.canverse.stocks.service.portfolio.model.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionInfo(
        @NotNull String id,
        @NotNull Transaction.Type type,
        @NotNull String portfolioId,
        @NotNull String positionId,
        @NotNull String symbol,
        @NotNull BigDecimal price,
        @NotNull BigDecimal quantity,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal profit,
        @NotNull Instant actionDate) {}
