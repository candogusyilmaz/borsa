package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.application.model.ReconciliationCursor;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationReadRepository;
import dev.canverse.stocks.ledger.web.response.ReconciliationPageResponse;
import dev.canverse.stocks.ledger.web.response.ReconciliationResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationReadService {

    private final ReconciliationReadRepository readRepository;
    private final LedgerCursorCodec cursorCodec;
    private final LedgerAccountAccess accountAccess;

    @Transactional(readOnly = true)
    public ReconciliationPageResponse list(UUID ownerUserAccountId, UUID accountId, int limit, String cursor) {
        accountAccess.owned(ownerUserAccountId, accountId);
        var filterDigest = cursorCodec.reconciliationFilterDigest(accountId);
        var decoded = cursor == null ? null : cursorCodec.decodeReconciliation(cursor, filterDigest);
        var rows = readRepository.findPage(ownerUserAccountId, accountId, decoded, limit + 1);
        var hasNext = rows.size() > limit;
        var page = hasNext ? rows.subList(0, limit) : rows;
        var next = hasNext
                ? cursorCodec.encodeReconciliation(new ReconciliationCursor(
                        filterDigest,
                        page.getLast().statementClosingAt(),
                        page.getLast().id()))
                : null;
        return new ReconciliationPageResponse(
                page.stream().map(ReconciliationResponse::from).toList(), next);
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse detail(UUID ownerUserAccountId, UUID reconciliationId) {
        return readRepository
                .findDetail(ownerUserAccountId, reconciliationId)
                .map(ReconciliationResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND));
    }
}
