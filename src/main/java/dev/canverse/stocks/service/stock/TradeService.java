package dev.canverse.stocks.service.stock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.*;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.stock.model.BuyTradeRequest;
import dev.canverse.stocks.service.stock.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.stock.model.SellTradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
                stockRepository.getReference(req.stockId()),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
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


    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview() {
        var tradePerformance = QTradePerformance.tradePerformance;
        var trade = QTrade.trade;
        var holding = QHolding.holding;
        var user = QUser.user;

        var month = trade.actionDate.month();
        var year = trade.actionDate.year();
        var profitSum = tradePerformance.profit.sumBigDecimal();

        var groupedData = queryFactory
                .select(
                        month,
                        year,
                        profitSum
                )
                .from(tradePerformance)
                .join(tradePerformance.trade, trade)
                .join(trade.holding, holding)
                .join(holding.user, user)
                .where(user.id.eq(AuthenticationProvider.getUser().getId()))
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
                                    tuple.get(profitSum)
                            ))
                            .collect(Collectors.toList());
                    return new MonthlyRevenueOverview(year2, months);
                })
                .collect(Collectors.toList());
    }
}
