package dev.canverse.stocks.service.client.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CanliBorsaVerileri(List<Item> data) {
    public record Item(
            String symbol,
            BigDecimal price,
            BigDecimal change,
            Instant time,
            BigDecimal changePercent,
            String volume
    ) {
    }
}
