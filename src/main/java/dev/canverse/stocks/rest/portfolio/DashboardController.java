package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.dashboard.DashboardService;
import dev.canverse.stocks.service.portfolio.model.dashboard.BasicDashboardView;
import dev.canverse.stocks.service.portfolio.model.dashboard.CreateDashboardRequest;
import dev.canverse.stocks.service.portfolio.model.dashboard.DashboardView;
import dev.canverse.stocks.service.portfolio.model.dashboard.TransactionInfo;
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

    @GetMapping("/default")
    public DashboardView getDefaultDashboard() {
        return dashboardService.getDefaultDashboard();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createDashboard(@Valid @RequestBody CreateDashboardRequest request) {
        dashboardService.create(request);
    }

    @GetMapping("/{dashboardId}/transactions")
    public List<TransactionInfo> getDashboardTransactions(@PathVariable Long dashboardId) {
        return dashboardService.getDashboardTransactions(dashboardId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{dashboardId}")
    public void deleteDashboard(@PathVariable Long dashboardId) {
        dashboardService.delete(dashboardId);
    }
}
