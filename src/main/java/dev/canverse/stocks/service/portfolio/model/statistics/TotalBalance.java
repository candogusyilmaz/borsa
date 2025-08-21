package dev.canverse.stocks.service.portfolio.model.statistics;

import java.math.BigDecimal;

public record TotalBalance(BigDecimal value, BigDecimal cost, BigDecimal percentageChange,
                           String currencyCode) {
}
