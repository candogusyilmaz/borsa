package dev.canverse.stocks.repository.custom;

import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.PortfolioInfo;

import java.util.List;

public interface PositionRepositoryCustom {
    PortfolioInfo fetchPortfolio(long portfolioId);

    List<BalanceHistory> fetchBalanceHistory(int lastDays, long userId);
}