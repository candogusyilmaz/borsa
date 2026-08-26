package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashActivityCommandService {

    private final EntityManager entityManager;
    private final LedgerAccountAccess accountAccess;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository postingRepository;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerReadRepository readRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;

    @Transactional
    public ActivityResponse recordCashActivity(UUID ownerUserAccountId, UUID accountId, CashActivityRequest request) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(request, "request");

        var amount = LedgerAmountParser.positive(request.amount(), "amount");
        requireCashActivityType(request.activityType());
        var observedAt = clock.instant();
        LedgerTimingRules.rejectFuture(request.effectiveAt(), observedAt, "effectiveAt");
        var hash = fingerprint.hash(fingerprint.values(
                "accountId", accountId.toString(),
                "activityType", request.activityType().name(),
                "amount", amount.canonical(),
                "recordingMode", request.recordingMode().name(),
                "effectiveAt", request.effectiveAt().toString(),
                "confirmPolicyBreach", request.confirmPolicyBreach(),
                "expectedBalanceVersion", request.expectedBalanceVersion()));
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.CASH_ACTIVITY, request.clientRequestId());
        var replay = idempotencyStore.replay(
                request.clientRequestId(),
                ownerUserAccountId,
                LedgerCommandScopes.CASH_ACTIVITY,
                hash,
                ActivityResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        requireCashActionAccount(account);
        var projection = accountAccess.projectionForUpdate(ownerUserAccountId, accountId);
        requireExpectedVersion(projection, request.expectedBalanceVersion());
        var delta = request.activityType() == ActivityType.CASH_DEPOSIT ? amount : amount.negate();
        var evaluation = LedgerPolicyEvaluator.evaluate(
                account, projection.balance(), delta, request.recordingMode(), request.confirmPolicyBreach());
        requireAllowed(evaluation);

        var activity = writeCashActivity(
                ownerUserAccountId, accountId, request, account, projection, delta, evaluation, observedAt);
        return saveResult(
                ownerUserAccountId,
                activity,
                hash,
                request.clientRequestId(),
                observedAt,
                LedgerCommandScopes.CASH_ACTIVITY);
    }

    @Transactional
    public ActivityResponse reverse(UUID ownerUserAccountId, UUID activityId, ReversalRequest request) {
        var observedAt = clock.instant();
        var hash = fingerprint.hash(fingerprint.values(
                "activityId", activityId.toString(),
                "correctionReason", request.correctionReason().trim()));
        commandLockRepository.lock(
                ownerUserAccountId, LedgerCommandScopes.ACTIVITY_REVERSAL, request.clientRequestId());
        var replay = idempotencyStore.replay(
                request.clientRequestId(),
                ownerUserAccountId,
                LedgerCommandScopes.ACTIVITY_REVERSAL,
                hash,
                ActivityResponse.class);
        if (replay != null) {
            return replay;
        }

        var original = activityRepository
                .findOwnedForUpdate(activityId, ownerUserAccountId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND));
        if (original.getActivityType() == ActivityType.OPENING_BALANCE
                || original.getActivityType() == ActivityType.REVERSAL
                || original.getActivityType() == ActivityType.RECONCILIATION_ADJUSTMENT) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
        if (activityRepository.findReversal(activityId, ownerUserAccountId).isPresent()) {
            throw new AppException(LedgerErrorCode.ACTIVITY_ALREADY_REVERSED);
        }
        var originalPostings = postingRepository.findOwnedActivity(ownerUserAccountId, activityId);
        if (originalPostings.isEmpty()) {
            throw new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND);
        }

        var accountIds = originalPostings.stream()
                .map(MoneyPosting::getFinancialAccountId)
                .distinct()
                .sorted()
                .toList();
        var accounts = accountAccess.lockAccounts(ownerUserAccountId, accountIds);
        var projections = accountAccess.lockProjections(ownerUserAccountId, accounts);
        var reversal = writeReversal(
                ownerUserAccountId,
                activityId,
                original,
                originalPostings,
                projections,
                request.correctionReason().trim(),
                request.clientRequestId(),
                observedAt);
        return saveResult(
                ownerUserAccountId,
                reversal,
                hash,
                request.clientRequestId(),
                observedAt,
                LedgerCommandScopes.ACTIVITY_REVERSAL);
    }

    private Activity writeCashActivity(
            UUID ownerUserAccountId,
            UUID accountId,
            CashActivityRequest request,
            FinancialAccount account,
            AccountBalanceProjection projection,
            FinancialAmount delta,
            LedgerPolicyEvaluator.PolicyEvaluation evaluation,
            Instant observedAt) {
        var activity = request.activityType() == ActivityType.CASH_DEPOSIT
                ? Activity.cashDeposit(
                        idGenerator.next(),
                        ownerUserAccountId,
                        request.clientRequestId(),
                        LedgerCommandScopes.CASH_ACTIVITY,
                        0,
                        request.recordingMode(),
                        request.effectiveAt(),
                        observedAt,
                        evaluation.decision())
                : Activity.cashWithdrawal(
                        idGenerator.next(),
                        ownerUserAccountId,
                        request.clientRequestId(),
                        LedgerCommandScopes.CASH_ACTIVITY,
                        0,
                        request.recordingMode(),
                        request.effectiveAt(),
                        observedAt,
                        evaluation.decision());
        activityRepository.save(activity);
        var posting = request.activityType() == ActivityType.CASH_DEPOSIT
                ? MoneyPosting.deposit(
                        idGenerator.next(),
                        ownerUserAccountId,
                        activity.getId(),
                        accountId,
                        projection.getCashPocket().getId(),
                        account.getCurrencyCode(),
                        delta,
                        observedAt)
                : MoneyPosting.withdrawal(
                        idGenerator.next(),
                        ownerUserAccountId,
                        activity.getId(),
                        accountId,
                        projection.getCashPocket().getId(),
                        account.getCurrencyCode(),
                        delta,
                        observedAt);
        postingRepository.save(posting);
        projection.apply(delta, observedAt, activity.getId(), observedAt);
        return activity;
    }

    private Activity writeReversal(
            UUID ownerUserAccountId,
            UUID activityId,
            Activity original,
            List<MoneyPosting> originalPostings,
            Map<UUID, AccountBalanceProjection> projections,
            String correctionReason,
            UUID clientRequestId,
            Instant observedAt) {
        var reversal = Activity.reversal(
                idGenerator.next(),
                ownerUserAccountId,
                clientRequestId,
                LedgerCommandScopes.ACTIVITY_REVERSAL,
                0,
                original.getEffectiveAt(),
                observedAt,
                correctionReason,
                activityId);
        activityRepository.save(reversal);
        for (var originalPosting : originalPostings) {
            var inverse = FinancialAmount.of(originalPosting.getAmount()).negate();
            postingRepository.save(MoneyPosting.reversal(
                    idGenerator.next(),
                    ownerUserAccountId,
                    reversal.getId(),
                    originalPosting.getFinancialAccountId(),
                    originalPosting.getCashPocketId(),
                    originalPosting.getCurrencyCode(),
                    inverse,
                    observedAt));
            projections
                    .get(originalPosting.getFinancialAccountId())
                    .apply(inverse, observedAt, reversal.getId(), observedAt);
        }
        return reversal;
    }

    private ActivityResponse saveResult(
            UUID ownerUserAccountId,
            Activity activity,
            String hash,
            UUID clientRequestId,
            Instant observedAt,
            String scope) {
        entityManager.flush();
        var response = readRepository
                .findActivity(ownerUserAccountId, activity.getId())
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND));
        idempotencyStore.save(
                ownerUserAccountId, scope, clientRequestId, hash, "ACTIVITY", activity.getId(), response, observedAt);
        return response;
    }

    private static void requireCashActivityType(ActivityType activityType) {
        if (activityType != ActivityType.CASH_DEPOSIT && activityType != ActivityType.CASH_WITHDRAWAL) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
    }

    private static void requireCashActionAccount(FinancialAccount account) {
        if (account.isArchived()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ARCHIVED);
        }
        if (account.getTrackingMode() != TrackingMode.FULL_LEDGER
                || account.getAccountKind().isLiability()
                || !account.getAccountKind().isCashFundingCapable()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
    }

    private static void requireAllowed(LedgerPolicyEvaluator.PolicyEvaluation evaluation) {
        if (!evaluation.allowed()) {
            throw new AppException(evaluation.errorCode());
        }
    }

    private static void requireExpectedVersion(AccountBalanceProjection projection, Long expectedVersion) {
        if (expectedVersion != null && projection.getVersion() != expectedVersion) {
            throw new AppException(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
        }
    }
}
