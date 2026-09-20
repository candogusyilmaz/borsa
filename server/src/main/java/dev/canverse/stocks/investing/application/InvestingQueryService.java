package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.InvestingReadRepository;
import dev.canverse.stocks.investing.infrastructure.PortfolioRepository;
import dev.canverse.stocks.investing.web.response.PositionResponse;
import dev.canverse.stocks.investing.web.response.TradeResponse;
import dev.canverse.stocks.investing.web.response.TradeSummaryResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.web.SliceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestingQueryService {

    private final InvestingReadRepository readRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional(readOnly = true)
    public TradeResponse getTrade(UUID ownerUserAccountId, UUID activityId) {
        return readRepository.findTrade(ownerUserAccountId, activityId).map(TradeResponse::from)
                .orElseThrow(() -> new AppException(InvestingErrorCode.TRADE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SliceResponse<TradeSummaryResponse> listTrades(UUID ownerUserAccountId, UUID accountId, UUID instrumentId, UUID portfolioId, Pageable pageable) {
        requireOwnedPortfolio(ownerUserAccountId, portfolioId);
        return readRepository.findTrades(ownerUserAccountId, accountId, instrumentId, portfolioId, pageable);
    }

    @Transactional(readOnly = true)
    public SliceResponse<PositionResponse> listOpenPositions(UUID ownerUserAccountId, UUID accountId, UUID portfolioId, Pageable pageable) {
        requireOwnedPortfolio(ownerUserAccountId, portfolioId);
        return readRepository.findOpenPositions(ownerUserAccountId, accountId, portfolioId, pageable);
    }

    @Transactional(readOnly = true)
    public PositionResponse getPosition(UUID ownerUserAccountId, UUID accountId, UUID instrumentId) {
        return readRepository.findPosition(ownerUserAccountId, accountId, instrumentId).map(PositionResponse::from)
                .orElseThrow(() -> new AppException(InvestingErrorCode.POSITION_NOT_FOUND));
    }

    private void requireOwnedPortfolio(UUID ownerUserAccountId, UUID portfolioId) {
        if (portfolioId != null && !portfolioRepository.existsOwned(ownerUserAccountId, portfolioId)) {
            throw new AppException(InvestingErrorCode.PORTFOLIO_NOT_FOUND);
        }
    }
}
