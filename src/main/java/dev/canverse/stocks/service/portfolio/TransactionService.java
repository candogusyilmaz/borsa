package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.InstrumentRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.repository.TransactionRepository;
import dev.canverse.stocks.service.portfolio.model.BuyTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.SellTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional
    public void buy(long portfolioId, BuyTransactionRequest req) {
        Portfolio portfolio = portfolioRepository.findPortfolioByPrincipal(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        var position = positionRepository.findByPortfolioAndInstrumentForPrincipal(
                portfolio.getId(),
                req.stockId()
        ).orElseGet(() -> positionRepository.save(new Position(
                portfolio,
                instrumentRepository.getReference(req.stockId())
        )));

        position.buy(BigDecimal.valueOf(req.quantity()), req.price(), req.commission(), req.actionDate());

        positionRepository.save(position);
    }

    @Transactional
    public void sell(long portfolioId, SellTransactionRequest req) {
        var position = positionRepository.findByPortfolioAndInstrumentForPrincipal(
                portfolioId,
                req.stockId()
        ).orElseThrow(() -> new NotFoundException("No holding found"));

        position.sell(BigDecimal.valueOf(req.quantity()), req.price(), req.commission(), req.actionDate());

        positionRepository.save(position);
    }

    @Transactional
    public void undoLatestTransaction(long portfolioId, long positionId) {
        var position = positionRepository
                .findByIdWithLatestTradeForPrincipal(positionId)
                .orElseThrow(() -> new NotFoundException("No holding found"));

        position.undo();
        positionRepository.save(position);
    }

    public TransactionHistory getTransactionHistory(long portfolioId) {
        if (!portfolioRepository.isPortfolioOwnedByPrincipal(portfolioId)) {
            throw new NotFoundException("Portfolio not found");
        }

        return transactionRepository.getTransactionHistory(portfolioId);
    }
}
