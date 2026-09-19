package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.InvestingReadRepository;
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

    @Transactional(readOnly = true)
    public TradeResponse getTrade(UUID ownerUserAccountId, UUID activityId) {
        return readRepository.findTrade(ownerUserAccountId, activityId).map(TradeResponse::from)
                .orElseThrow(() -> new AppException(InvestingErrorCode.TRADE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SliceResponse<TradeSummaryResponse> listTrades(UUID ownerUserAccountId, UUID accountId, UUID instrumentId, Pageable pageable) {
        return readRepository.findTrades(ownerUserAccountId, accountId, instrumentId, pageable);
    }

    @Transactional(readOnly = true)
    public SliceResponse<PositionResponse> listOpenPositions(UUID ownerUserAccountId, UUID accountId, Pageable pageable) {
        return readRepository.findOpenPositions(ownerUserAccountId, accountId, pageable);
    }

    @Transactional(readOnly = true)
    public PositionResponse getPosition(UUID ownerUserAccountId, UUID accountId, UUID instrumentId) {
        return readRepository.findPosition(ownerUserAccountId, accountId, instrumentId).map(PositionResponse::from)
                .orElseThrow(() -> new AppException(InvestingErrorCode.POSITION_NOT_FOUND));
    }
}
