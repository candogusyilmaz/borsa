package dev.canverse.stocks.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.domain.entity.QPortfolio;
import dev.canverse.stocks.domain.entity.QTrade;
import dev.canverse.stocks.domain.entity.Trade;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.PortfolioRepository;
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
    private final PortfolioRepository portfolioRepository;

    @Override
    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview(long portfolioId) {
        if (!portfolioRepository.isPortfolioOwnedByPrincipal(portfolioId)) {
            throw new NotFoundException("Portfolio not found");
        }

        var trade = QTrade.trade;
        var holding = QHolding.holding;
        var portfolio = QPortfolio.portfolio;

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
                .join(holding.portfolio, portfolio)
                .where(trade.type.eq(Trade.Type.SELL).and(portfolio.id.eq(portfolioId)))
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
    public TradeHistory getTradeHistory(long portfolioId) {
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
                .where(trade.holding.portfolio.id.eq(portfolioId))
                .orderBy(trade.createdAt.desc())
                .fetch();

        return new TradeHistory(query);
    }
}
