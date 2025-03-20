package dev.canverse.stocks.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.domain.entity.QTrade;
import dev.canverse.stocks.domain.entity.QUser;
import dev.canverse.stocks.domain.entity.Trade;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TradeRepositoryCustomImpl implements TradeRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview(Long userId) {
        var trade = QTrade.trade;
        var holding = QHolding.holding;
        var user = QUser.user;

        var month = trade.actionDate.month();
        var year = trade.actionDate.year();
        var q = trade.performance.profit.sumBigDecimal();

        var groupedData = queryFactory
                .select(
                        month,
                        year,
                        q
                )
                .from(trade)
                .join(trade.holding, holding)
                .join(holding.user, user)
                .where(trade.type.eq(Trade.Type.SELL).and(user.id.eq(userId)))
                .groupBy(year, month)
                .fetch();

        var dataByYear = groupedData.stream()
                .collect(Collectors.groupingBy(tuple -> tuple.get(year)));

        return dataByYear.entrySet().stream()
                .map(entry -> {
                    int year2 = entry.getKey();
                    List<MonthlyRevenueOverview.Month> months = entry.getValue().stream()
                            .map(tuple -> new MonthlyRevenueOverview.Month(
                                    tuple.get(month),
                                    tuple.get(q)
                            ))
                            .collect(Collectors.toList());
                    return new MonthlyRevenueOverview(year2, months);
                })
                .collect(Collectors.toList());
    }

    @Override
    public TradeHistory getTradeHistory(Long userId) {
        var trade = QTrade.trade;
        var subTrade = new QTrade("subTrade");

        var isLatestExpr = JPAExpressions
                .selectOne()
                .from(subTrade)
                .where(subTrade.holding.eq(trade.holding).and(subTrade.id.gt(trade.id)))
                .notExists();

        var query = queryFactory.select(
                        Projections.constructor(TradeHistory.Item.class,
                                trade.actionDate,
                                trade.createdAt,
                                trade.type,
                                trade.holding.id.stringValue(),
                                trade.holding.stock.symbol,
                                trade.price,
                                trade.quantity,
                                trade.performance.profit,
                                trade.performance.returnPercentage,
                                trade.performance.performanceCategory,
                                isLatestExpr
                        )
                )
                .from(trade)
                .leftJoin(trade.performance)
                .where(trade.holding.user.id.eq(userId))
                .orderBy(trade.createdAt.desc())
                .fetch();

        return new TradeHistory(query);
    }
}
