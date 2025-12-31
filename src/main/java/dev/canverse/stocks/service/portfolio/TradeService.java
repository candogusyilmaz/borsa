package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.entity.instrument.MarketCurrencyId;
import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.*;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import dev.canverse.stocks.service.portfolio.model.TradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioAccessValidator portfolioAccessValidator;
    private final MarketCurrencyRepository marketCurrencyRepository;

    private final TradeMapper tradeMapper;

    @Transactional
    public void buy(long portfolioId, TradeRequest req) {
        var portfolio = portfolioAccessValidator.validateAccess(portfolioId);
        
        var instrument = instrumentRepository.findById(req.stockId())
                .orElseThrow(() -> new NotFoundException(
                        String.format("No instrument found for stock id %s", req.stockId())
                ));

        marketCurrencyRepository.findById(new MarketCurrencyId(instrument.getMarket().getId(), req.currencyCode()))
                .orElseThrow(() -> new NotFoundException(
                        String.format("No market currency found for stock id %s and currency %s",
                                req.stockId(),
                                req.currencyCode())
                ));

        var position = positionRepository.findBy(
                AuthenticationProvider.getUser().getId(),
                portfolio.getId(),
                req.stockId(),
                req.currencyCode()
        ).orElseGet(() -> positionRepository.save(new Position(
                portfolio,
                instrumentRepository.getReference(req.stockId()),
                req.currencyCode()
        )));

        var buy = position.buy(req.quantity(), req.price(), req.commission(), req.actionDate());
        buy.getMetadata().setNotes(req.notes());
        buy.getMetadata().setTags(req.tags());

        positionRepository.saveAndFlush(position);
    }

    @Transactional
    public void sell(long portfolioId, TradeRequest req) {
        var position = positionRepository.findBy(
                AuthenticationProvider.getUser().getId(),
                portfolioId,
                req.stockId(),
                req.currencyCode()
        ).orElseThrow(() -> new NotFoundException(
                String.format("No holding found for stock id %s", req.stockId())
        ));

        var sell = position.sell(req.quantity(), req.price(), req.commission(), req.actionDate());
        sell.getMetadata().setNotes(req.notes());
        sell.getMetadata().setTags(req.tags());

        positionRepository.saveAndFlush(position);
    }

    @Transactional
    public void undoLatestTrade(long positionId) {
        var position = positionRepository
                .findByIdAndUserId(positionId, AuthenticationProvider.getUser().getId())
                .orElseThrow(() -> new NotFoundException("No holding found"));

        portfolioAccessValidator.validateAccess(position.getPortfolio().getId());

        position.undo();
        positionRepository.save(position);
    }

    public TradeHistory fetchTradeHistory(long portfolioId) {
        portfolioAccessValidator.validateAccess(portfolioId);

        return tradeRepository.fetchTradeHistory(portfolioId);
    }

    public List<TradeInfo> fetchTrades(Long userId) {
        return tradeMapper.fetchTrades(userId);
    }

    public List<TradeInfo> fetchActiveTrades(Long userId, Long positionId) {
        return tradeMapper.fetchActiveTrades(userId, positionId);
    }
}
