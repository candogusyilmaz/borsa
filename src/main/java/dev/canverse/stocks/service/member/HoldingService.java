package dev.canverse.stocks.service.member;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.domain.entity.QHoldingHistory;
import dev.canverse.stocks.domain.entity.QTrade;
import dev.canverse.stocks.domain.entity.QTradePerformance;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.member.model.Balance;
import dev.canverse.stocks.service.member.model.BalanceHistory;
import dev.canverse.stocks.service.member.model.TradeHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldingService {
    private final JPAQueryFactory queryFactory;
    private final HoldingRepository holdingRepository;

    @Transactional
    public void deleteAllHoldings() {
        holdingRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }

    public Balance fetchBalance() {
        var holding = QHolding.holding;

        var query = queryFactory.select(
                        Projections.constructor(Balance.Stock.class,
                                holding.stock.id.stringValue(),
                                holding.stock.symbol,
                                holding.quantity,
                                holding.averagePrice,
                                holding.stock.snapshot.last)
                )
                .from(holding)
                .where(holding.user.id.eq(AuthenticationProvider.getUser().getId()).and(holding.quantity.gt(0)))
                .orderBy(holding.stock.symbol.asc())
                .fetch();

        return query.isEmpty() ? new Balance(List.of()) : new Balance(query);
    }

    public List<BalanceHistory> fetchBalanceHistory(int lastDays) {
        var holdingHistory = QHoldingHistory.holdingHistory;

        var startDate = LocalDate.now().minusDays(lastDays);
        var startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return queryFactory
                .select(
                        Projections.constructor(
                                BalanceHistory.class,
                                holdingHistory.createdAt,
                                holdingHistory.holding.stock.symbol,
                                holdingHistory.averagePrice.multiply(holdingHistory.quantity).sumBigDecimal()
                        )
                )
                .from(holdingHistory)
                .where(holdingHistory.createdAt.after(startInstant).and(holdingHistory.holding.user.id.eq(AuthenticationProvider.getUser().getId())))
                .groupBy(holdingHistory.createdAt, holdingHistory.holding.stock.symbol)
                .orderBy(holdingHistory.createdAt.desc())
                .fetch();
    }

    public TradeHistory fetchTradeHistory() {
        var trade = QTrade.trade;
        var p = QTradePerformance.tradePerformance;

        var query = queryFactory.select(
                        Projections.constructor(TradeHistory.Item.class,
                                trade.actionDate,
                                trade.createdAt,
                                trade.type,
                                trade.holding.stock.symbol,
                                trade.price,
                                trade.quantity,
                                p.profit,
                                p.returnPercentage,
                                p.performanceCategory
                        )
                )
                .from(trade)
                .leftJoin(trade.performance, p)
                .where(trade.holding.user.id.eq(AuthenticationProvider.getUser().getId()))
                .orderBy(trade.createdAt.desc())
                .fetch();

        return new TradeHistory(query);
    }
}
