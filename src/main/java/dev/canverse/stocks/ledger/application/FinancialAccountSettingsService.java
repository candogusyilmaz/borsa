package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.web.request.AccountMetadataRequest;
import dev.canverse.stocks.ledger.web.request.AccountPolicyRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialAccountSettingsService {

    private final EntityManager entityManager;
    private final LedgerAccountAccess accountAccess;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerReadRepository readRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;

    @Transactional
    public FinancialAccountResponse updateMetadata(
            UUID ownerUserAccountId, UUID accountId, AccountMetadataRequest request) {
        var observedAt = clock.instant();
        validateTimeZone(request.timeZone());
        var hash = fingerprint.hash(fingerprint.values(
                "accountId", accountId.toString(),
                "name", request.name().trim(),
                "timeZone", request.timeZone().trim(),
                "version", request.version()));
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.ACCOUNT_METADATA, request.clientRequestId());
        var replay = idempotencyStore.replay(
                request.clientRequestId(),
                ownerUserAccountId,
                LedgerCommandScopes.ACCOUNT_METADATA,
                hash,
                FinancialAccountResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        requireVersion(account, request.version());
        account.updateMetadata(request.name(), request.timeZone().trim(), observedAt);
        return saveResult(
                ownerUserAccountId,
                account,
                LedgerCommandScopes.ACCOUNT_METADATA,
                request.clientRequestId(),
                hash,
                observedAt);
    }

    @Transactional
    public FinancialAccountResponse updatePolicy(
            UUID ownerUserAccountId, UUID accountId, AccountPolicyRequest request) {
        var observedAt = clock.instant();
        var authorizedLimit = LedgerAmountParser.optional(request.authorizedLimit(), "authorizedLimit");
        var hash = fingerprint.hash(fingerprint.values(
                "accountId",
                accountId.toString(),
                "policy",
                request.policy() == null ? null : request.policy().name(),
                "authorizedLimit",
                authorizedLimit == null ? null : authorizedLimit.canonical(),
                "version",
                request.version()));
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.ACCOUNT_POLICY, request.clientRequestId());
        var replay = idempotencyStore.replay(
                request.clientRequestId(),
                ownerUserAccountId,
                LedgerCommandScopes.ACCOUNT_POLICY,
                hash,
                FinancialAccountResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        requireVersion(account, request.version());
        try {
            account.updatePolicy(request.policy(), authorizedLimit, observedAt);
        } catch (IllegalArgumentException exception) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED, exception);
        }
        return saveResult(
                ownerUserAccountId,
                account,
                LedgerCommandScopes.ACCOUNT_POLICY,
                request.clientRequestId(),
                hash,
                observedAt);
    }

    private FinancialAccountResponse saveResult(
            UUID ownerUserAccountId,
            FinancialAccount account,
            String scope,
            UUID clientRequestId,
            String hash,
            Instant observedAt) {
        entityManager.flush();
        var response = readRepository
                .findAccount(ownerUserAccountId, account.getId())
                .map(FinancialAccountResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
        idempotencyStore.save(
                ownerUserAccountId,
                scope,
                clientRequestId,
                hash,
                "FINANCIAL_ACCOUNT",
                account.getId(),
                response,
                observedAt);
        return response;
    }

    private static void requireVersion(FinancialAccount account, long expectedVersion) {
        if (account.getVersion() != expectedVersion) {
            throw new AppException(LedgerErrorCode.ACCOUNT_VERSION_CONFLICT);
        }
    }

    private static void validateTimeZone(String timeZone) {
        try {
            FinancialAccount.requireIanaTimeZone(timeZone);
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField(
                    "timeZone", "error.fields.ledger.invalid_timezone", "The time zone must be an IANA zone.");
        }
    }
}
