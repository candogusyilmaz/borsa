package dev.canverse.stocks.service.stock.model;

import java.math.BigDecimal;
import java.util.List;

public record TradesHeatMap(int year, List<Month> data) {
    public record Month(int month, BigDecimal profit) {
    }
}
