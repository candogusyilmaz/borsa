package dev.canverse.stocks.service.marketupdater;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record Snapshot(String currencyCode, BigDecimal last,
                       BigDecimal previousClose,
                       Timestamp updatedAt,
                       long instrumentId
) {
}
