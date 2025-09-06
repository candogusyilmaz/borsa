package dev.canverse.stocks.service.portfolio.model.dashboard;

import java.util.List;

public record CreateDashboardRequest(
        String name,
        String currencyId,
        List<String> portfolioIds
) {
}
