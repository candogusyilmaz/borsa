package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.TradeRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final TradeRepository tradeRepository;

    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview() {
        return tradeRepository.getMonthlyRevenueOverview(AuthenticationProvider.getUser().getId());
    }
}
