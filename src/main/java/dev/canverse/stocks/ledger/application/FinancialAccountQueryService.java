package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.web.response.BalanceResponse;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialAccountQueryService {

    private final LedgerReadRepository readRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FinancialAccountResponse> list(UUID ownerUserAccountId, boolean includeArchived) {
        return readRepository.findAccounts(ownerUserAccountId, includeArchived).stream()
                .map(FinancialAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialAccountResponse get(UUID ownerUserAccountId, UUID accountId) {
        return readAccount(ownerUserAccountId, accountId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BalanceResponse balance(UUID ownerUserAccountId, UUID accountId, Instant asOf) {
        var observedAt = clock.instant();
        var requestedAsOf = asOf == null ? observedAt : asOf;
        if (requestedAsOf.isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
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
