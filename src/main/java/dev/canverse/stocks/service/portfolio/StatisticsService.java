package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.custom.StatisticsRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final StatisticsRepository statisticsRepository;

    public RealizedGains getRealizedGains(String periodType, String targetCurrency) {
        return statisticsRepository.getRealizedGains(AuthenticationProvider.getUser().getId(), periodType, List.of(), targetCurrency);
    }

    public DailyChange getDailyChange(String targetCurrency) {
        return statisticsRepository.getDailyChange(AuthenticationProvider.getUser().getId(), List.of(), targetCurrency);
    }

    public TotalBalance getTotalBalance(String targetCurrency) {
        return statisticsRepository.getTotalBalance(AuthenticationProvider.getUser().getId(), List.of(), targetCurrency);
    }
}
