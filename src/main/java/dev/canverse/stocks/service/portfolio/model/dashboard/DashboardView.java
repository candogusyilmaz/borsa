package dev.canverse.stocks.service.portfolio.model.dashboard;

import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;

public record DashboardView(String id, String name, String currencyCode, boolean isDefault, DailyChange dailyChange,
                            RealizedGains realizedGains, TotalBalance totalBalance) {
}
