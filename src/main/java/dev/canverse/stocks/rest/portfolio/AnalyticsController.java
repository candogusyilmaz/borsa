package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.AnalyticsService;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/monthly-revenue-overview")
    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview() {
        return analyticsService.getMonthlyRevenueOverview();
    }
}
