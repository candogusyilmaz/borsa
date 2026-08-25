package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.application.model.AccountCursor;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.web.response.BalanceResponse;
import dev.canverse.stocks.ledger.web.response.FinancialAccountPageResponse;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialAccountQueryService {

    private final LedgerReadRepository readRepository;
    private final LedgerCursorCodec cursorCodec;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FinancialAccountPageResponse list(
            UUID ownerUserAccountId, boolean includeArchived, int limit, String cursor) {
        var filterDigest = cursorCodec.accountFilterDigest(includeArchived);
        var decoded = cursor == null ? null : cursorCodec.decodeAccount(cursor, filterDigest);
        var rows = readRepository.findAccounts(ownerUserAccountId, includeArchived, decoded, limit + 1);
        var hasNext = rows.size() > limit;
        var page = hasNext ? rows.subList(0, limit) : rows;
        var next = hasNext
                ? cursorCodec.encodeAccount(new AccountCursor(
                        filterDigest,
                        page.getLast().nameNormalized(),
                        page.getLast().id()))
                : null;
        return new FinancialAccountPageResponse(
                page.stream().map(FinancialAccountResponse::from).toList(), next);
    }

    @Transactional(readOnly = true)
    public FinancialAccountResponse get(UUID ownerUserAccountId, UUID accountId) {
        return readAccount(ownerUserAccountId, accountId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BalanceResponse balance(UUID ownerUserAccountId, UUID accountId, Instant asOf) {
        var observedAt = clock.instant();
        var requestedAsOf = asOf == null ? observedAt : asOf;
        LedgerTimingRules.rejectFuture(requestedAsOf, observedAt, "asOf");
        return readRepository
                .findBalance(
                        ownerUserAccountId,
                        accountId,
                        requestedAsOf,
                        asOf == null ? observedAt : requestedAsOf,
                        asOf == null)
                .map(BalanceResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    private FinancialAccountResponse readAccount(UUID ownerUserAccountId, UUID accountId) {
        return readRepository
                .findAccount(ownerUserAccountId, accountId)
                .map(FinancialAccountResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }
}
