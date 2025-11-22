package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.InstrumentRepository;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.repository.TradeMapper;
import dev.canverse.stocks.repository.TradeRepository;
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

    private final TradeMapper tradeMapper;

    @Transactional
    public void buy(long portfolioId, TradeRequest req) {
        var portfolio = portfolioAccessValidator.validateAccess(portfolioId);

        var position = positionRepository.findByPortfolioAndInstrumentForPrincipal(
                portfolio.getId(),
                req.stockId()
        ).orElseGet(() -> positionRepository.save(new Position(
                portfolio,
                instrumentRepository.getReference(req.stockId())
        )));

        var buy = position.buy(req.quantity(), req.price(), req.commission(), req.actionDate());
        buy.getMetadata().setNotes(req.notes());
        buy.getMetadata().setTags(req.tags());

        positionRepository.saveAndFlush(position);
    }

    @Transactional
    public void sell(long portfolioId, TradeRequest req) {
        portfolioAccessValidator.validateAccess(portfolioId);

        var position = positionRepository.findByPortfolioAndInstrumentForPrincipal(
                portfolioId,
                req.stockId()
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
