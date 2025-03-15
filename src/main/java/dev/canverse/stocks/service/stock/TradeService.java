package dev.canverse.stocks.service.stock;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.*;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.member.model.TradeHistory;
import dev.canverse.stocks.service.stock.model.BuyTradeRequest;
import dev.canverse.stocks.service.stock.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.stock.model.SellTradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final StockRepository stockRepository;
    private final HoldingRepository holdingRepository;
    private final JPAQueryFactory queryFactory;

    @Transactional
    public void buy(BuyTradeRequest req) {
        var holding = holdingRepository.findByUserIdAndStockId(
                AuthenticationProvider.getUser().getId(),
                req.stockId()
        ).orElseGet(() -> holdingRepository.save(new Holding(
                AuthenticationProvider.getUser(),
                stockRepository.getReference(req.stockId())
        )));

        holding.buy(req.quantity(), req.price(), req.tax(), req.actionDate());

        holdingRepository.save(holding);
    }

    @Transactional
    public void sell(SellTradeRequest req) {
        var holding = holdingRepository.findByUserIdAndStockId(
                AuthenticationProvider.getUser().getId(),
                req.stockId()
        ).orElseThrow(() -> new NotFoundException("No holding found"));

        holding.sell(req.quantity(), req.price(), req.tax(), req.actionDate());

        holdingRepository.save(holding);
    }

    @Transactional
    public void undoLatestTrade(Long holdingId) {
        var holding = holdingRepository.findByIdWithLatestTrade(holdingId)
                .orElseThrow(() -> new NotFoundException("No holding found"));

        holding.undo();
        holdingRepository.save(holding);
    }

    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview() {
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
                .where(trade.type.eq(Trade.Type.SELL).and(user.id.eq(AuthenticationProvider.getUser().getId())))
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

    public TradeHistory fetchTrades() {
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
                .where(trade.holding.user.id.eq(AuthenticationProvider.getUser().getId()))
                .orderBy(trade.createdAt.desc())
                .fetch();

        return new TradeHistory(query);
    }
}
