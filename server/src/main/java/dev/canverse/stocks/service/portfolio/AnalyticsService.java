package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.TradeRepository;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TradeRepository transactionRepository;

    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview(long portfolioId) {
        return transactionRepository.getMonthlyRevenueOverview(portfolioId);
    }
}
