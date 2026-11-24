package dev.canverse.stocks.service.marketupdater;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
        String symbol,
        String name,
        String currencyCode,
        BigDecimal last,
        BigDecimal previousClose,
        Instant updatedAt) {}
