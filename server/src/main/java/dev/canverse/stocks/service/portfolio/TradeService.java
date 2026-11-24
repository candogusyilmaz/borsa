package dev.canverse.stocks.service.portfolio;

import static dev.canverse.stocks.service.common.TransactionCallbacks.afterCommit;

import dev.canverse.stocks.domain.entity.instrument.MarketCurrencyId;
import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.*;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.FetchTradesQuery;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import dev.canverse.stocks.service.portfolio.model.TradeRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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

        var instrument =
                instrumentRepository
                        .findById(req.stockId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                String.format(
                                                        "No instrument found for stock id %s",
                                                        req.stockId())));

        marketCurrencyRepository
                .findById(new MarketCurrencyId(instrument.getMarket().getId(), req.currencyCode()))
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        String.format(
                                                "No market currency found for stock id %s and currency %s",
                                                req.stockId(), req.currencyCode())));

        var position =
                positionRepository
                        .findBy(
                                AuthenticationProvider.getUser().getId(),
                                portfolio.getId(),
                                req.stockId(),
                                req.currencyCode())
                        .orElseGet(
                                () ->
                                        positionRepository.save(
                                                new Position(
                                                        portfolio,
                                                        instrumentRepository.getReference(
                                                                req.stockId()),
                                                        req.currencyCode())));

        var buy = position.buy(req.quantity(), req.price(), req.commission(), req.actionDate());
        buy.getMetadata().setNotes(req.notes());
        buy.getMetadata().setTags(req.tags());

        positionRepository.saveAndFlush(position);

        var resolvedPortfolioId = portfolio.getId();
        var instrumentId = req.stockId();
        var currencyCode = req.currencyCode();
        afterCommit(
                () ->
                        log.atInfo()
                                .addKeyValue("event", "trade_created")
                                .addKeyValue("tradeType", "BUY")
                                .addKeyValue("portfolioId", resolvedPortfolioId)
                                .addKeyValue("instrumentId", instrumentId)
                                .addKeyValue("currencyCode", currencyCode)
                                .log("Trade created"));
    }

    @Transactional
    public void sell(long portfolioId, TradeRequest req) {
        var position =
                positionRepository
                        .findBy(
                                AuthenticationProvider.getUser().getId(),
                                portfolioId,
                                req.stockId(),
                                req.currencyCode())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                String.format(
                                                        "No holding found for stock id %s",
                                                        req.stockId())));

        var sell = position.sell(req.quantity(), req.price(), req.commission(), req.actionDate());
        sell.getMetadata().setNotes(req.notes());
        sell.getMetadata().setTags(req.tags());

        positionRepository.saveAndFlush(position);

        var instrumentId = req.stockId();
        var currencyCode = req.currencyCode();
        afterCommit(
                () ->
                        log.atInfo()
                                .addKeyValue("event", "trade_created")
                                .addKeyValue("tradeType", "SELL")
                                .addKeyValue("portfolioId", portfolioId)
                                .addKeyValue("instrumentId", instrumentId)
                                .addKeyValue("currencyCode", currencyCode)
                                .log("Trade created"));
    }

    @Transactional
    public void undoLatestTrade(long positionId) {
        var position =
                positionRepository
                        .findByIdAndUserId(positionId, AuthenticationProvider.getUser().getId())
                        .orElseThrow(() -> new NotFoundException("No holding found"));

        var portfolioId = position.getPortfolio().getId();
        portfolioAccessValidator.validateAccess(portfolioId);

        position.undo();
        positionRepository.save(position);

        afterCommit(
                () ->
                        log.atInfo()
                                .addKeyValue("event", "trade_deleted")
                                .addKeyValue("portfolioId", portfolioId)
                                .addKeyValue("positionId", positionId)
                                .log("Latest trade undone"));
    }

    public TradeHistory fetchTradeHistory(long portfolioId) {
        portfolioAccessValidator.validateAccess(portfolioId);

        return tradeRepository.fetchTradeHistory(portfolioId);
    }

    public List<TradeInfo> fetchTrades(Long userId, FetchTradesQuery query) {
        return tradeMapper.fetchTrades(userId, query);
    }

    public List<TradeInfo> fetchActiveTrades(Long userId, Long positionId) {
        return tradeMapper.fetchActiveTrades(userId, positionId);
    }
}
