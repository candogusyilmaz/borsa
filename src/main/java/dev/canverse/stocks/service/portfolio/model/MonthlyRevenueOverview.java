package dev.canverse.stocks.service.portfolio.model;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyRevenueOverview(@NotNull int year, @NotNull List<@NotNull Month> data) {
    public record Month(@NotNull int month, @NotNull BigDecimal profit) {
    }
}
