package dev.canverse.stocks.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.domain.entity.QHoldingHistory;
import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class HoldingRepositoryCustomImpl implements HoldingRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public Portfolio fetchPortfolio(boolean includeCommission, long userId) {
        var holding = QHolding.holding;

        var averagePrice = (includeCommission ?
                holding.total.add(holding.commission).divide(holding.quantity) :
                holding.total.divide(holding.quantity)).castToNum(BigDecimal.class);

        var result = queryFactory.select(
                        Projections.constructor(Portfolio.Stock.class,
                                holding.stock.id.stringValue(),
                                holding.stock.symbol,
                                holding.stock.snapshot.dailyChange,
                                holding.stock.snapshot.previousClose,
                                holding.stock.snapshot.dailyChangePercent,
                                holding.quantity,
                                holding.total,
                                holding.commission,
                                holding.stock.snapshot.last,
                                averagePrice,
                                holding.stock.snapshot.dailyChange.multiply(holding.quantity),
                                holding.stock.snapshot.last.multiply(holding.quantity)
                        )
                )
                .from(holding)
                .where(holding.user.id.eq(userId).and(holding.quantity.gt(0)))
                .orderBy(holding.stock.symbol.asc())
                .fetch();

        return new Portfolio(result);
    }

    @Override
    public List<BalanceHistory> fetchBalanceHistory(int lastDays, long userId) {
        var holdingHistory = QHoldingHistory.holdingHistory;

        var startDate = LocalDate.now().minusDays(lastDays);
        var startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return queryFactory
                .select(
                        Projections.constructor(
                                BalanceHistory.class,
                                holdingHistory.createdAt,
                                holdingHistory.holding.stock.symbol,
                                holdingHistory.total
                        )
                )
                .from(holdingHistory)
                .where(holdingHistory.createdAt.after(startInstant).and(holdingHistory.holding.user.id.eq(userId)))
                .groupBy(holdingHistory.createdAt, holdingHistory.holding.stock.symbol)
                .orderBy(holdingHistory.createdAt.desc())
                .fetch();
    }
}
