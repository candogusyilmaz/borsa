package dev.canverse.stocks.service.marketupdater;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record Snapshot(BigDecimal last,
                       BigDecimal previousClose,
                       Timestamp updatedAt,
                       long instrumentId
) {
}
