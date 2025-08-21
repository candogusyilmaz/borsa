package dev.canverse.stocks.repository.custom;

import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;

import java.util.List;

public interface TransactionRepositoryCustom {
    List<MonthlyRevenueOverview> getMonthlyRevenueOverview(long portfolioId);

    TransactionHistory getTransactionHistory(long portfolioId);
}