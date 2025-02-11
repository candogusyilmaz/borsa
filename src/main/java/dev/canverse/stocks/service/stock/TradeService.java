package dev.canverse.stocks.service.stock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.domain.entity.QTradePerformance;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.stock.model.BuyTradeRequest;
import dev.canverse.stocks.service.stock.model.SellTradeRequest;
import dev.canverse.stocks.service.stock.model.TradesHeatMap;
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


    public List<TradesHeatMap> getTradesHeatMap() {
        var tradePerformance = QTradePerformance.tradePerformance;

        var month = tradePerformance.trade.actionDate.month();
        var year = tradePerformance.trade.actionDate.year();
        var profitSum = tradePerformance.profit.sumBigDecimal();

        var groupedData = queryFactory
                .select(
                        month,
                        year,
                        profitSum
                )
                .from(tradePerformance)
                .where(tradePerformance.trade.holding.user.id.eq(AuthenticationProvider.getUser().getId()))
                .groupBy(year, month)
                .fetch();

        // Group the fetched tuples by year.
        var dataByYear = groupedData.stream()
                .collect(Collectors.groupingBy(tuple -> tuple.get(year)));

        // Map each year group to a TradesHeatMap record.
        return dataByYear.entrySet().stream()
                .map(entry -> {
                    int year2 = entry.getKey();
                    List<TradesHeatMap.Month> months = entry.getValue().stream()
                            .map(tuple -> new TradesHeatMap.Month(
                                    tuple.get(month),
                                    tuple.get(profitSum)
                            ))
                            .collect(Collectors.toList());
                    return new TradesHeatMap(year2, months);
                })
                .collect(Collectors.toList());
    }
}
