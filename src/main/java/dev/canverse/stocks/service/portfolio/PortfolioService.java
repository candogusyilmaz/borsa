package dev.canverse.stocks.service.portfolio;


import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.domain.entity.portfolio.QPosition;
import dev.canverse.stocks.repository.DashboardRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BasicPortfolioView;
import dev.canverse.stocks.service.portfolio.model.PortfolioInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final DashboardRepository dashboardRepository;

    private final PortfolioAccessValidator portfolioAccessValidator;
    private final JPAQueryFactory queryFactory;

    public PortfolioInfo fetchPortfolio(long portfolioId) {
        portfolioAccessValidator.validateAccess(portfolioId);

        var position = QPosition.position;

        var result = queryFactory.select(
                        Projections.constructor(PortfolioInfo.Stock.class,
                                position.instrument.id.stringValue(),
                                position.instrument.symbol,
                                position.instrument.snapshot.dailyChange,
                                position.instrument.snapshot.previousClose,
                                position.instrument.snapshot.dailyChangePercent,
                                position.quantity,
                                position.total,
                                position.instrument.snapshot.last,
                                position.total.divide(position.quantity).castToNum(BigDecimal.class),
                                position.instrument.snapshot.dailyChange.multiply(position.quantity),
                                position.instrument.snapshot.last.multiply(position.quantity)
                        )
                )
                .from(position)
                .leftJoin(position.instrument.snapshot)
                .where(position.portfolio.id.eq(portfolioId)
                        .and(position.quantity.gt(0))
                        .and(position.portfolio.user.id.eq(AuthenticationProvider.getUser().getId()))
                )
                .orderBy(position.instrument.symbol.asc())
                .fetch();

        return new PortfolioInfo(result);
    }

    @Transactional
    public void createPortfolio(String name) {
        var portfolio = new Portfolio(AuthenticationProvider.getUser(), name);
        portfolioRepository.save(portfolio);

        var defaultDashboard = dashboardRepository.findDefaultByUserId(AuthenticationProvider.getUser().getId());
        defaultDashboard.addPortfolio(portfolio);
        dashboardRepository.save(defaultDashboard);
    }

    @Transactional
    public void archivePortfolio(long portfolioId) {
        var portfolio = portfolioAccessValidator.validateAccess(portfolioId);

        portfolio.archive();

        portfolioRepository.save(portfolio);
    }

    public List<BasicPortfolioView> fetchBasicPortfolioViews() {
        return portfolioRepository.findAllByUserId(AuthenticationProvider.getUser().getId()).stream()
                .filter(s -> !s.isArchived())
                .map(p -> new BasicPortfolioView(p.getId().toString(), p.getName()))
                .toList();
    }
}