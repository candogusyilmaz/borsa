package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.web.request.ArchiveAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningCorrectionRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialAccountLifecycleService {

    private final EntityManager entityManager;
    private final LedgerAccountAccess accountAccess;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository postingRepository;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerReadRepository readRepository;
    private final AccountBalanceProjectionRepository projectionRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;

    @Transactional
    public FinancialAccountResponse archive(UUID ownerUserAccountId, UUID accountId, ArchiveAccountRequest request) {
        var observedAt = clock.instant();
        var hash = fingerprint.hash(fingerprint.values("accountId", accountId.toString(), "version", request.version()));
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.ACCOUNT_ARCHIVE, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, LedgerCommandScopes.ACCOUNT_ARCHIVE, hash,
                FinancialAccountResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        if (account.getVersion() != request.version()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_VERSION_CONFLICT);
        }
        account.archive(observedAt);
        return saveResult(ownerUserAccountId, account, LedgerCommandScopes.ACCOUNT_ARCHIVE, request.clientRequestId(), hash, observedAt);
    }

    @Transactional
    public FinancialAccountResponse correctOpening(UUID ownerUserAccountId, UUID accountId, OpeningCorrectionRequest request) {
        var observedAt = clock.instant();
        var replacementAmount = LedgerAmountParser.exact(request.amount(), "amount");
        if (Objects.requireNonNull(request.effectiveAt(), "effectiveAt").isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
        var hash = openingCorrectionFingerprint(accountId, replacementAmount, request.effectiveAt(), request.correctionReason().trim(), request.version());
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.OPENING_CORRECTION, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, LedgerCommandScopes.OPENING_CORRECTION, hash,
                FinancialAccountResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        if (account.getVersion() != request.version()) {
            throw new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT);
        }
        if (!account.isFullLedger() || account.getCurrentOpeningActivityId() == null) {
            throw new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT);
        }
        var original = activityRepository.findOwnedForUpdate(account.getCurrentOpeningActivityId(), ownerUserAccountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT));
        if (!original.getEffectiveAt().equals(request.effectiveAt())) {
            throw new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT);
        }
        var originalPostings = postingRepository.findOwnedActivity(ownerUserAccountId, original.getId());
        if (originalPostings.isEmpty()) {
            throw new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT);
        }

        var replacement = writeOpeningCorrection(ownerUserAccountId, account, original, originalPostings, replacementAmount, request.correctionReason().trim(),
                request.clientRequestId(), observedAt);
        account.setCurrentOpeningActivity(replacement.getId());
        return saveResult(ownerUserAccountId, account, LedgerCommandScopes.OPENING_CORRECTION, request.clientRequestId(), hash, observedAt);
    }

    private String openingCorrectionFingerprint(UUID accountId, FinancialAmount amount, Instant effectiveAt, String correctionReason, Long version) {
        return fingerprint.hash(fingerprint.values("accountId", accountId.toString(), "amount", amount.canonical(), "effectiveAt", effectiveAt.toString(),
                "correctionReason", correctionReason, "version", version));
    }

    private Activity writeOpeningCorrection(UUID ownerUserAccountId, FinancialAccount account, Activity original, List<MoneyPosting> originalPostings,
            FinancialAmount replacementAmount, String correctionReason, UUID clientRequestId, Instant observedAt) {
        var inverse = Activity.reversal(idGenerator.next(), ownerUserAccountId, clientRequestId, LedgerCommandScopes.OPENING_CORRECTION, 1,
                original.getEffectiveAt(), observedAt, correctionReason, original.getId());
        activityRepository.save(inverse);
        for (var originalPosting : originalPostings) {
            postingRepository.save(MoneyPosting.reversal(idGenerator.next(), ownerUserAccountId, inverse.getId(), originalPosting.getFinancialAccountId(),
                    originalPosting.getCashPocketId(), originalPosting.getCurrencyCode(), FinancialAmount.of(originalPosting.getAmount()).negate(),
                    observedAt));
        }

        var replacementDecision = replacementAmount.isNegative() ? PolicyDecision.HISTORICAL_BREACH_RECORDED : PolicyDecision.ALLOWED;
        var replacement = Activity.correctedOpeningBalance(idGenerator.next(), ownerUserAccountId, clientRequestId, LedgerCommandScopes.OPENING_CORRECTION, 2,
                original.getEffectiveAt(), observedAt, replacementDecision, correctionReason, original.getId());
        activityRepository.save(replacement);

        var originalPosting = originalPostings.getFirst();
        postingRepository.save(MoneyPosting.opening(idGenerator.next(), ownerUserAccountId, replacement.getId(), account.getId(),
                originalPosting.getCashPocketId(), originalPosting.getCurrencyCode(), replacementAmount, observedAt));

        var projection = projectionRepository.findOwnedForUpdate(ownerUserAccountId, account.getId())
                .orElseThrow(() -> new AppException(LedgerErrorCode.OPENING_STATE_CONFLICT));
        var oldAmount = FinancialAmount.of(originalPosting.getAmount());
        projection.apply(replacementAmount.subtract(oldAmount), observedAt, replacement.getId(), observedAt);
        return replacement;
    }

    private FinancialAccountResponse saveResult(UUID ownerUserAccountId, FinancialAccount account, String scope, UUID clientRequestId, String hash,
            Instant observedAt) {
        entityManager.flush();
        var response = readRepository.findAccount(ownerUserAccountId, account.getId()).map(FinancialAccountResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
        idempotencyStore.save(ownerUserAccountId, scope, clientRequestId, hash, "FINANCIAL_ACCOUNT", account.getId(), response, observedAt);
        return response;
    }
}
