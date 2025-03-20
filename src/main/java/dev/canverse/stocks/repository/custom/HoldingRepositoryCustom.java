package dev.canverse.stocks.repository.custom;

import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.Portfolio;

import java.util.List;

public interface HoldingRepositoryCustom {
    Portfolio fetchPortfolio(boolean includeCommission, long userId);

    List<BalanceHistory> fetchBalanceHistory(int lastDays, long userId);
}