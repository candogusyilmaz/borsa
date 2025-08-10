package dev.canverse.stocks.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.portfolio.QPosition;
import dev.canverse.stocks.domain.entity.portfolio.QPositionHistory;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.PortfolioInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryCustomImpl implements PositionRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public PortfolioInfo fetchPortfolio(long portfolioId) {
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

    @Override
    public List<BalanceHistory> fetchBalanceHistory(int lastDays, long portfolioId) {
        var positionHistory = QPositionHistory.positionHistory;

        var startDate = LocalDate.now().minusDays(lastDays);
        var startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return queryFactory
                .select(
                        Projections.constructor(
                                BalanceHistory.class,
                                positionHistory.createdAt,
                                positionHistory.position.instrument.symbol,
                                positionHistory.total
                        )
                )
                .from(positionHistory)
                .where(positionHistory.createdAt.after(startInstant).and(positionHistory.position.portfolio.id.eq(portfolioId)))
                .groupBy(positionHistory.createdAt, positionHistory.position.instrument.symbol)
                .orderBy(positionHistory.createdAt.desc())
                .fetch();
    }
}
