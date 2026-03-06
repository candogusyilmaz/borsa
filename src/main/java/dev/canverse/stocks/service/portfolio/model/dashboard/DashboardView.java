package dev.canverse.stocks.service.portfolio.model.dashboard;

import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;

import jakarta.validation.constraints.NotNull;

public record DashboardView(
        @NotNull String id,
        @NotNull String name,
        @NotNull String currencyCode,
        @NotNull boolean isDefault,
        @NotNull DailyChange dailyChange,
        @NotNull RealizedGains realizedGains,
        @NotNull TotalBalance totalBalance) {}
