package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.repository.StockSplitRepository;
import dev.canverse.stocks.repository.TradeRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BuyTradeRequest;
import dev.canverse.stocks.service.portfolio.model.SellTradeRequest;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.chrono.ChronoLocalDate;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final StockRepository stockRepository;
    private final StockSplitRepository stockSplitRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

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

        var latestSplit = stockSplitRepository.findLatestProcessedSplitByStockId(holding.getStock().getId());

        if (latestSplit.isPresent() && !holding.getTrades().isEmpty()) {
            var latestTradeDate = holding.getTrades().stream()
                    .findFirst()
                    .map(trade -> ChronoLocalDate.from(trade.getActionDate()))
                    .orElseThrow(() -> new IllegalStateException("Trade has no action date"));

            if (latestSplit.get().getEffectiveDate().isAfter(latestTradeDate)) {
                throw new IllegalStateException("Cannot undo trade after stock split");
            }
        }

        holding.undo();
        holdingRepository.save(holding);
    }

    public TradeHistory fetchTrades() {
        return tradeRepository.getTradeHistory(AuthenticationProvider.getUser().getId());
    }
}
