package dev.canverse.stocks.service.portfolio;

import static dev.canverse.stocks.service.common.TransactionCallbacks.afterCommit;

import com.querydsl.jpa.impl.JPAQueryFactory;

import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.repository.DashboardRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BasicPortfolioView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final DashboardRepository dashboardRepository;

    private final PortfolioAccessValidator portfolioAccessValidator;
    private final JPAQueryFactory queryFactory;

    @Transactional
    public void createPortfolio(String name) {
        var portfolio = new Portfolio(AuthenticationProvider.getUser(), name, "#3b82f6");
        var saved = portfolioRepository.save(portfolio);

        var defaultDashboard =
                dashboardRepository.findDefaultByUserId(AuthenticationProvider.getUser().getId());
        defaultDashboard.addPortfolio(portfolio);
        dashboardRepository.save(defaultDashboard);

        var portfolioId = saved != null ? saved.getId() : portfolio.getId();
        afterCommit(
                () -> {
                    var builder = log.atInfo().addKeyValue("event", "portfolio_created");
                    if (portfolioId != null) {
                        builder.addKeyValue("portfolioId", portfolioId);
                    }
                    builder.log("Portfolio created");
                });
    }

    @Transactional
    public void archivePortfolio(long portfolioId) {
        var portfolio = portfolioAccessValidator.validateAccess(portfolioId);

        portfolio.archive();

        portfolioRepository.save(portfolio);

        afterCommit(
                () ->
                        log.atInfo()
                                .addKeyValue("event", "portfolio_archived")
                                .addKeyValue("portfolioId", portfolioId)
                                .log("Portfolio archived"));
    }

    public List<BasicPortfolioView> fetchBasicPortfolioViews() {
        return portfolioRepository
                .findAllByUserId(AuthenticationProvider.getUser().getId())
                .stream()
                .filter(s -> !s.isArchived())
                .map(p -> new BasicPortfolioView(p.getId().toString(), p.getName()))
                .toList();
    }
}
