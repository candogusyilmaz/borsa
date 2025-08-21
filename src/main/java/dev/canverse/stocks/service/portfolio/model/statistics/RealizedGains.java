package dev.canverse.stocks.service.portfolio.model.statistics;

import java.math.BigDecimal;

public record RealizedGains(BigDecimal currentPeriod, BigDecimal previousPeriod, BigDecimal percentageChange,
                            String currencyCode) {
}
