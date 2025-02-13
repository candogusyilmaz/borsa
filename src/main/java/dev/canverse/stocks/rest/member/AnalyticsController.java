package dev.canverse.stocks.rest.member;

import dev.canverse.stocks.service.stock.TradeService;
import dev.canverse.stocks.service.stock.model.MonthlyRevenueOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final TradeService tradeService;

    @GetMapping("/monthly-revenue-overview")
    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview() {
        return tradeService.getMonthlyRevenueOverview();
    }
}
