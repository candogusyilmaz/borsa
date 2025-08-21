package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.TransactionRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.portfolio.model.RealizedGains;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TransactionRepository transactionRepository;

    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview(long portfolioId) {
        return transactionRepository.getMonthlyRevenueOverview(portfolioId);
    }

    public RealizedGains getRealizedGains(String periodType) {
        return transactionRepository.getRealizedGains(AuthenticationProvider.getUser().getId(), periodType, "TRY");
    }
}
