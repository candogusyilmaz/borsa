package dev.canverse.stocks.service.portfolio.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceHistory(Instant date, String stock, BigDecimal balance) {
}
