package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationReadRepository;
import dev.canverse.stocks.ledger.web.response.ReconciliationResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.web.SliceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationReadService {

    private final ReconciliationReadRepository readRepository;
    private final LedgerAccountAccess accountAccess;

    @Transactional(readOnly = true)
    public SliceResponse<ReconciliationResponse> list(UUID ownerUserAccountId, UUID accountId, Pageable pageable) {
        accountAccess.owned(ownerUserAccountId, accountId);
        return readRepository.findReconciliations(ownerUserAccountId, accountId, pageable);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse detail(UUID ownerUserAccountId, UUID reconciliationId) {
        return readRepository
                .findDetail(ownerUserAccountId, reconciliationId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND));
    }
}
