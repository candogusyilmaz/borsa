package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.AnalyticsService;
import dev.canverse.stocks.service.portfolio.model.RealizedGains;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/realized-gains")
    public RealizedGains getRealizedGains(@RequestParam(required = false, defaultValue = "month") String periodType) {
        return analyticsService.getRealizedGains(periodType);
    }
}
