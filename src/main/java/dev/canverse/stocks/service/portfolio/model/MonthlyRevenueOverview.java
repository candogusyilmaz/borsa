package dev.canverse.stocks.service.portfolio.model;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyRevenueOverview(int year, List<Month> data) {
    public record Month(int month, BigDecimal profit) {
    }
}
