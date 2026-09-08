package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.web.SliceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashActivityQueryService {

    private final LedgerReadRepository readRepository;

    @Transactional(readOnly = true)
    public SliceResponse<ActivityResponse> list(UUID ownerUserAccountId, UUID accountId, Pageable pageable) {
        return readRepository.findActivities(ownerUserAccountId, accountId, pageable);
    }

    @Transactional(readOnly = true)
    public ActivityResponse get(UUID ownerUserAccountId, UUID activityId) {
        return readRepository.findActivity(ownerUserAccountId, activityId).orElseThrow(() -> new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND));
    }
}
