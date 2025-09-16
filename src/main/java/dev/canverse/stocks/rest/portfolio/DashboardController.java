package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.DashboardService;
import dev.canverse.stocks.service.portfolio.model.dashboard.BasicDashboardView;
import dev.canverse.stocks.service.portfolio.model.dashboard.CreateDashboardRequest;
import dev.canverse.stocks.service.portfolio.model.dashboard.DashboardView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboards")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping
    public List<BasicDashboardView> getAllDashboards() {
        return dashboardService.getAllDashboards();
    }

    @GetMapping("/{dashboardId}")
    public DashboardView getDashboard(@PathVariable Long dashboardId) {
        return dashboardService.getDashboard(dashboardId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createDashboard(@Valid @RequestBody CreateDashboardRequest request) {
        dashboardService.create(request);
    }

    @GetMapping("/{dashboardId}/transactions")
    public Object getDashboardTransactions(@PathVariable Long dashboardId) {
        return dashboardService.getDashboardTransactions(dashboardId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{dashboardId}")
    public void deleteDashboard(@PathVariable Long dashboardId) {
        dashboardService.delete(dashboardId);
    }

/*@GetMapping

    @PutMapping("/{dashboardId}")
    public Dashboard updateDashboard(@PathVariable Long dashboardId, @Valid @RequestBody DashboardUpdateRequest request) {
        return dashboardService.updateDashboard(dashboardId, request);
    }

    @PutMapping("/{dashboardId}/portfolios/{portfolioId}")
    public Dashboard addPortfolioToDashboard(@PathVariable Long dashboardId, @PathVariable Long portfolioId) {
        return dashboardService.addPortfolioToDashboard(dashboardId, portfolioId);
    }

    @DeleteMapping("/{dashboardId}/portfolios/{portfolioId}")
    public Dashboard removePortfolioFromDashboard(@PathVariable Long dashboardId, @PathVariable Long portfolioId) {
        return dashboardService.removePortfolioFromDashboard(dashboardId, portfolioId);
    }*/
}
