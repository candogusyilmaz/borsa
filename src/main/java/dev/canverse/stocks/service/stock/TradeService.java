package dev.canverse.stocks.service.stock;

import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.domain.entity.Trade;
import dev.canverse.stocks.domain.entity.TradePerformance;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.repository.TradePerformanceRepository;
import dev.canverse.stocks.repository.TradeRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.stock.model.BuyTradeRequest;
import dev.canverse.stocks.service.stock.model.SellTradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final HoldingRepository holdingRepository;
    private final TradePerformanceRepository tradePerformanceRepository;

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

        holding.buy(req.quantity(), req.price(), req.tax());

        tradeRepository.save(new Trade(
                AuthenticationProvider.getUser(),
                stockRepository.getReference(req.stockId()),
                Trade.Type.BUY,
                req.quantity(),
                req.price(),
                req.tax()
        ));
        holdingRepository.save(holding);
    }

    @Transactional
    public void sell(SellTradeRequest req) {
        var holding = holdingRepository.findByUserIdAndStockId(
                AuthenticationProvider.getUser().getId(),
                req.stockId()
        ).orElseThrow(() -> new NotFoundException("No holding found"));

        holding.sell(req.quantity(), req.tax());

        Trade trade = tradeRepository.save(new Trade(
                AuthenticationProvider.getUser(),
                stockRepository.getReference(req.stockId()),
                Trade.Type.SELL,
                req.quantity(),
                req.price(),
                req.tax()
        ));

        tradePerformanceRepository.save(new TradePerformance(
                trade,
                req.price().subtract(holding.getAveragePrice()).multiply(BigDecimal.valueOf(req.quantity())),
                req.price().subtract(holding.getAveragePrice()).divide(holding.getAveragePrice(), RoundingMode.HALF_UP)
        ));


        holdingRepository.save(holding);
    }
}
