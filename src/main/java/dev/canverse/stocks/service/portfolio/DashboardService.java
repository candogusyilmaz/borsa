package dev.canverse.stocks.service.portfolio;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.portfolio.*;
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
import dev.canverse.stocks.service.portfolio.model.dashboard.TransactionInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public void delete(Long dashboardId) {
        var dashboard = dashboardRepository.findByIdAndUserId(dashboardId, AuthenticationProvider.getUser().getId())
                .orElseThrow(() -> new NotFoundException("Dashboard not found."));

        if (dashboard.isDefault()) {
            throw new BadRequestException("Cannot delete the default dashboard.");
        }

        dashboardRepository.delete(dashboard);
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

    public DashboardView getDefaultDashboard() {
        var d = QDashboard.dashboard;

        var dashboardId = qf.select(d.id)
                .from(d)
                .where(d.user.id.eq(AuthenticationProvider.getUser().getId()).and(d.isDefault.isTrue()))
                .fetchOne();

        if (dashboardId == null) {
            throw new NotFoundException("Default dashboard not found.");
        }

        return getDashboard(dashboardId);
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
                .where(dpf.dashboard.id.eq(dashboardId).and(dpf.portfolio.archived.isFalse()))
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

    public List<TransactionInfo> getDashboardTransactions(Long dashboardId) {
        var dashboard = dashboardRepository.findByIdAndUserId(dashboardId, AuthenticationProvider.getUser().getId())
                .orElseThrow(() -> new NotFoundException("Dashboard not found."));

        var t = QTransaction.transaction;
        var dp = QDashboardPortfolio.dashboardPortfolio;
        var p = QPosition.position;

        var sq = qf.select(p.id)
                .from(dp)
                .join(dp.portfolio.positions, p)
                .where(dp.dashboard.id.eq(dashboardId).and(dp.portfolio.archived.isFalse()))
                .fetch();

        var currencyConvertedProfit = Expressions.stringTemplate("public.convert_currency({0}, {1}, {2})",
                t.performance.profit,
                t.position.instrument.denominationCurrency,
                dashboard.getCurrency().getCode()).castToNum(BigDecimal.class);

        return qf.select(Projections.constructor(TransactionInfo.class,
                        t.id.stringValue(),
                        t.type,
                        t.position.portfolio.id.stringValue(),
                        t.position.id.stringValue(),
                        t.position.instrument.symbol,
                        t.price,
                        t.quantity,
                        currencyConvertedProfit,
                        t.actionDate))
                .from(t)
                .leftJoin(t.performance)
                .where(t.position.id.in(sq))
                .orderBy(t.actionDate.desc())
                .fetch();
    }
}
