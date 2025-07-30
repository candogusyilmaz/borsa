package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.entity.Holding;
import dev.canverse.stocks.domain.entity.Portfolio;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.*;
import dev.canverse.stocks.service.portfolio.model.BuyTradeRequest;
import dev.canverse.stocks.service.portfolio.model.SellTradeRequest;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TradeService {
    private final StockRepository stockRepository;
    private final StockSplitRepository stockSplitRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional
    public void buy(long portfolioId, BuyTradeRequest req) {
        Portfolio portfolio = portfolioRepository.findPortfolioByPrincipal(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        var holding = holdingRepository.findByPortfolioIdAndStockIdForPrincipal(
                portfolio.getId(),
                req.stockId()
        ).orElseGet(() -> holdingRepository.save(new Holding(
                portfolio,
                stockRepository.getReference(req.stockId())
        )));

        holding.buy(req.quantity(), req.price(), req.commission(), req.actionDate());

        holdingRepository.save(holding);
    }

    @Transactional
    public void sell(long portfolioId, SellTradeRequest req) {
        var holding = holdingRepository.findByPortfolioIdAndStockIdForPrincipal(
                portfolioId,
                req.stockId()
        ).orElseThrow(() -> new NotFoundException("No holding found"));

        holding.sell(req.quantity(), req.price(), req.commission(), req.actionDate());

        holdingRepository.save(holding);
    }

    @Transactional
    public void undoLatestTrade(long portfolioId, long holdingId) {
        var holding = holdingRepository.findByIdWithLatestTradeForPrincipal(holdingId)
                .orElseThrow(() -> new NotFoundException("No holding found"));

        var latestSplit = stockSplitRepository.findLatestProcessedSplitByStockId(holding.getStock().getId());

        if (latestSplit.isPresent() && !holding.getTrades().isEmpty()) {
            var latestTradeDate = LocalDate.from(holding.getTrades().getFirst().getActionDate());

            if (latestSplit.get().getEffectiveDate().isAfter(latestTradeDate)) {
                throw new IllegalStateException("Cannot undo trade after stock split");
            }
        }

        holding.undo();
        holdingRepository.save(holding);
    }

    public TradeHistory fetchTrades(long portfolioId) {
        if (!portfolioRepository.isPortfolioOwnedByPrincipal(portfolioId)) {
            throw new NotFoundException("Portfolio not found");
        }

        return tradeRepository.getTradeHistory(portfolioId);
    }
}
