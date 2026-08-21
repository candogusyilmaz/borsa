package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.FinancialAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Applies the owner scope and deterministic lock order to ledger aggregate access. */
@Component
@RequiredArgsConstructor
final class LedgerAccountAccess {

    private final FinancialAccountRepository accountRepository;
    private final AccountBalanceProjectionRepository projectionRepository;

    FinancialAccount owned(UUID ownerUserAccountId, UUID accountId) {
        return accountRepository
                .findOwned(accountId, ownerUserAccountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    FinancialAccount ownedForUpdate(UUID ownerUserAccountId, UUID accountId) {
        return accountRepository
                .findOwnedForUpdate(accountId, ownerUserAccountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    AccountBalanceProjection projection(UUID ownerUserAccountId, UUID accountId) {
        return projectionRepository
                .findOwned(ownerUserAccountId, accountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    AccountBalanceProjection projectionForUpdate(UUID ownerUserAccountId, UUID accountId) {
        return projectionRepository
                .findOwnedForUpdate(ownerUserAccountId, accountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED));
    }

    LinkedHashMap<UUID, FinancialAccount> lockAccounts(UUID ownerUserAccountId, UUID... accountIds) {
        return lockAccounts(ownerUserAccountId, List.of(accountIds));
    }

    LinkedHashMap<UUID, FinancialAccount> lockAccounts(UUID ownerUserAccountId, List<UUID> accountIds) {
        var sortedIds = accountIds.stream().distinct().sorted().toList();
        var accounts = new LinkedHashMap<UUID, FinancialAccount>();
        for (var accountId : sortedIds) {
            accounts.put(accountId, ownedForUpdate(ownerUserAccountId, accountId));
        }
        return accounts;
    }

    LinkedHashMap<UUID, AccountBalanceProjection> lockProjections(
            UUID ownerUserAccountId, Map<UUID, FinancialAccount> accounts) {
        var projections = new LinkedHashMap<UUID, AccountBalanceProjection>();
        for (var accountId : accounts.keySet()) {
            projections.put(accountId, projectionForUpdate(ownerUserAccountId, accountId));
        }
        return projections;
    }

    Map<UUID, FinancialAccount> loadTransferAccounts(UUID ownerUserAccountId, UUID sourceId, UUID destinationId) {
        var accounts = new LinkedHashMap<UUID, FinancialAccount>();
        accounts.put(sourceId, owned(ownerUserAccountId, sourceId));
        accounts.put(destinationId, owned(ownerUserAccountId, destinationId));
        return accounts;
    }
}
