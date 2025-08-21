package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.StatisticsService;
import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    @GetMapping("/realized-gains")
    public RealizedGains getRealizedGains(@RequestParam(required = false, defaultValue = "month") String periodType) {
        return statisticsService.getRealizedGains(AuthenticationProvider.getUser().getId(), periodType, "TRY");
    }

    @GetMapping("/daily-change")
    public DailyChange getDailyChange(@RequestParam(required = false) Long portfolioId) {
        return statisticsService.getDailyChange(AuthenticationProvider.getUser().getId(), portfolioId, "TRY");
    }

    @GetMapping("/total-balance")
    public TotalBalance getTotalBalance(@RequestParam(required = false) Long portfolioId) {
        return statisticsService.getTotalBalance(AuthenticationProvider.getUser().getId(), portfolioId, "TRY");
    }
}
