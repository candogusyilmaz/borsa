package dev.canverse.stocks.repository.custom;

import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;

import java.util.List;

public interface TradeRepositoryCustom {
    List<MonthlyRevenueOverview> getMonthlyRevenueOverview(Long userId);

    TradeHistory getTradeHistory(Long userId);
}