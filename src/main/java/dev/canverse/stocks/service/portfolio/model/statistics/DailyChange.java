package dev.canverse.stocks.service.portfolio.model.statistics;

import java.math.BigDecimal;

public record DailyChange(BigDecimal currentValue, BigDecimal previousValue, BigDecimal percentageChange,
                          String currencyCode) {
}
