package dev.canverse.stocks.service.portfolio;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import dev.canverse.stocks.domain.entity.portfolio.QDashboard;
import dev.canverse.stocks.domain.entity.portfolio.QDashboardPortfolio;
import dev.canverse.stocks.domain.exception.BadRequestException;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.CurrencyRepository;
import dev.canverse.stocks.repository.DashboardRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.custom.StatisticsRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.dashboard.BasicDashboardView;
import dev.canverse.stocks.service.portfolio.model.dashboard.CreateDashboardRequest;
import dev.canverse.stocks.service.portfolio.model.dashboard.DashboardView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final CurrencyRepository currencyRepository;
    private final StatisticsRepository statisticsRepository;
    private final PortfolioRepository portfolioRepository;
    private final DashboardRepository dashboardRepository;

    private final JPAQueryFactory qf;

    public void create(@Valid CreateDashboardRequest request) {

        var dashboard = new Dashboard(AuthenticationProvider.getUser(), request.name(), currencyRepository.getReferenceById(Long.valueOf(request.currencyId())));

        request.portfolioIds().stream().map(Long::valueOf).forEach(s -> {
            if (!portfolioRepository.existsByUserId(AuthenticationProvider.getUser().getId(), s)) {
                throw new BadRequestException("One or more portfolios are invalid.");
            }

            dashboard.addPortfolio(portfolioRepository.getReference(s));
        });

        dashboardRepository.save(dashboard);
    }

    public List<BasicDashboardView> getAllDashboards() {
        var d = QDashboard.dashboard;

        return qf.select(Projections.constructor(BasicDashboardView.class,
                        d.id.stringValue(),
                        d.name,
                        d.isDefault))
                .from(d)
                .where(d.user.id.eq(AuthenticationProvider.getUser().getId()))
                .fetch();
    }

    public DashboardView getDashboard(Long dashboardId) {
        var userId = AuthenticationProvider.getUser().getId();
        var d = QDashboard.dashboard;

        var dashboard = qf.select(
                        d.id.stringValue(),
                        d.name,
                        d.isDefault,
                        d.currency.code
                )
                .from(d)
                .where(d.user.id.eq(userId).and(d.id.eq(dashboardId)))
                .fetchOne();

        if (dashboard == null) {
            throw new NotFoundException("Dashboard not found.");
        }

        var dpf = QDashboardPortfolio.dashboardPortfolio;
        var portfolioIds = qf.select(dpf.portfolio.id)
                .from(dpf)
                .where(dpf.dashboard.id.eq(dashboardId))
                .fetch();

        var realizedGains = statisticsRepository.getRealizedGains(userId, "month", portfolioIds, dashboard.get(d.currency.code));
        var dailyChange = statisticsRepository.getDailyChange(userId, portfolioIds, dashboard.get(d.currency.code));
        var totalBalance = statisticsRepository.getTotalBalance(userId, portfolioIds, dashboard.get(d.currency.code));

        return new DashboardView(
                dashboard.get(d.id.stringValue()),
                dashboard.get(d.name),
                dashboard.get(d.currency.code),
                dashboard.get(d.isDefault),
                dailyChange,
                realizedGains,
                totalBalance
        );
    }
}
