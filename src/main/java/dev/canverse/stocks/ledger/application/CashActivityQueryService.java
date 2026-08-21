package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.application.model.ActivityCursor;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.web.response.ActivityPageResponse;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashActivityQueryService {

    private final LedgerReadRepository readRepository;
    private final LedgerCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public ActivityPageResponse list(UUID ownerUserAccountId, UUID accountId, int limit, String cursor) {
        var filterDigest = cursorCodec.activityFilterDigest(accountId);
        var decoded = cursor == null ? null : cursorCodec.decodeActivity(cursor, filterDigest);
        var rows = readRepository.findActivities(ownerUserAccountId, accountId, decoded, limit + 1);
        var hasNext = rows.size() > limit;
        var page = hasNext ? rows.subList(0, limit) : rows;
        var next = hasNext
                ? cursorCodec.encodeActivity(new ActivityCursor(
                        filterDigest,
                        page.getLast().recordedAt(),
                        page.getLast().id()))
                : null;
        return new ActivityPageResponse(
                page.stream().map(ActivityResponse::from).toList(), next);
    }

    @Transactional(readOnly = true)
    public ActivityResponse get(UUID ownerUserAccountId, UUID activityId) {
        return readRepository
                .findActivity(ownerUserAccountId, activityId)
                .map(ActivityResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND));
    }
}
