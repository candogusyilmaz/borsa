package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.application.model.TransferPreviewView;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.web.request.TransferPreviewRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.ledger.web.response.TransferPreviewResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashTransferService {

    private final EntityManager entityManager;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository postingRepository;
    private final LedgerAccountAccess accountAccess;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerReadRepository readRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;

    @Transactional(readOnly = true)
    public TransferPreviewResponse preview(UUID ownerUserAccountId, TransferPreviewRequest request) {
        var amount = LedgerAmountParser.positive(request.amount(), "amount");
        var observedAt = clock.instant();
        LedgerTimingRules.rejectFuture(request.effectiveAt(), observedAt, "effectiveAt");
        var accounts = accountAccess.loadTransferAccounts(
                ownerUserAccountId, request.sourceAccountId(), request.destinationAccountId());
        var source = accounts.get(request.sourceAccountId());
        var destination = accounts.get(request.destinationAccountId());
        validateTransferShape(source, destination);
        var sourceProjection = accountAccess.projection(ownerUserAccountId, source.getId());
        var destinationProjection = accountAccess.projection(ownerUserAccountId, destination.getId());
        var sourceEvaluation = LedgerPolicyEvaluator.evaluate(
                source,
                sourceProjection.balance(),
                amount.negate(),
                request.recordingMode(),
                request.confirmPolicyBreach());
        var destinationEvaluation = LedgerPolicyEvaluator.evaluate(
                destination,
                destinationProjection.balance(),
                amount,
                request.recordingMode(),
                request.confirmPolicyBreach());
        return TransferPreviewResponse.from(new TransferPreviewView(
                source.getId(),
                destination.getId(),
                source.getCurrencyCode(),
                amount.canonical(),
                sourceProjection.balance().canonical(),
                sourceProjection.balance().add(amount.negate()).canonical(),
                destinationProjection.balance().canonical(),
                destinationProjection.balance().add(amount).canonical(),
                sourceEvaluation.decision(),
                destinationEvaluation.decision(),
                sourceProjection.getVersion(),
                destinationProjection.getVersion(),
                sourceEvaluation.allowed() && destinationEvaluation.allowed()));
    }

    @Transactional
    public ActivityResponse transfer(UUID ownerUserAccountId, TransferRequest request) {
        var amount = LedgerAmountParser.positive(request.amount(), "amount");
        var observedAt = clock.instant();
        LedgerTimingRules.rejectFuture(request.effectiveAt(), observedAt, "effectiveAt");
        var hash = fingerprint.hash(fingerprint.values(
                "sourceAccountId", request.sourceAccountId().toString(),
                "destinationAccountId", request.destinationAccountId().toString(),
                "amount", amount.canonical(),
                "recordingMode", request.recordingMode().name(),
                "effectiveAt", request.effectiveAt().toString(),
                "confirmPolicyBreach", request.confirmPolicyBreach(),
                "expectedSourceBalanceVersion", request.expectedSourceBalanceVersion(),
                "expectedDestinationBalanceVersion", request.expectedDestinationBalanceVersion()));
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.TRANSFER, request.clientRequestId());
        var replay = idempotencyStore.replay(
                request.clientRequestId(),
                ownerUserAccountId,
                LedgerCommandScopes.TRANSFER,
                hash,
                ActivityResponse.class);
        if (replay != null) {
            return replay;
        }

        var accounts = accountAccess.lockAccounts(
                ownerUserAccountId, request.sourceAccountId(), request.destinationAccountId());
        var source = accounts.get(request.sourceAccountId());
        var destination = accounts.get(request.destinationAccountId());
        validateTransferShape(source, destination);
        var projections = accountAccess.lockProjections(ownerUserAccountId, accounts);
        var sourceProjection = projections.get(source.getId());
        var destinationProjection = projections.get(destination.getId());
        requireExpectedVersion(sourceProjection, request.expectedSourceBalanceVersion());
        requireExpectedVersion(destinationProjection, request.expectedDestinationBalanceVersion());
        var sourceEvaluation = LedgerPolicyEvaluator.evaluate(
                source,
                sourceProjection.balance(),
                amount.negate(),
                request.recordingMode(),
                request.confirmPolicyBreach());
        var destinationEvaluation = LedgerPolicyEvaluator.evaluate(
                destination,
                destinationProjection.balance(),
                amount,
                request.recordingMode(),
                request.confirmPolicyBreach());
        requireAllowed(sourceEvaluation);
        requireAllowed(destinationEvaluation);

        var activity = writeTransfer(
                ownerUserAccountId,
                request,
                source,
                destination,
                sourceProjection,
                destinationProjection,
                amount,
                sourceEvaluation,
                destinationEvaluation,
                observedAt);
        return saveResult(ownerUserAccountId, activity, hash, request.clientRequestId(), observedAt);
    }

    private Activity writeTransfer(
            UUID ownerUserAccountId,
            TransferRequest request,
            FinancialAccount source,
            FinancialAccount destination,
            AccountBalanceProjection sourceProjection,
            AccountBalanceProjection destinationProjection,
            FinancialAmount amount,
            LedgerPolicyEvaluator.PolicyEvaluation sourceEvaluation,
            LedgerPolicyEvaluator.PolicyEvaluation destinationEvaluation,
            Instant observedAt) {
        var activity = Activity.ownedTransfer(
                idGenerator.next(),
                ownerUserAccountId,
                request.clientRequestId(),
                LedgerCommandScopes.TRANSFER,
                0,
                request.recordingMode(),
                request.effectiveAt(),
                observedAt,
                combinedDecision(sourceEvaluation, destinationEvaluation));
        activityRepository.save(activity);
        postingRepository.save(MoneyPosting.transferSource(
                idGenerator.next(),
                ownerUserAccountId,
                activity.getId(),
                source.getId(),
                sourceProjection.getCashPocket().getId(),
                source.getCurrencyCode(),
                amount.negate(),
                observedAt));
        postingRepository.save(MoneyPosting.transferDestination(
                idGenerator.next(),
                ownerUserAccountId,
                activity.getId(),
                destination.getId(),
                destinationProjection.getCashPocket().getId(),
                destination.getCurrencyCode(),
                amount,
                observedAt));
        sourceProjection.apply(amount.negate(), observedAt, activity.getId(), observedAt);
        destinationProjection.apply(amount, observedAt, activity.getId(), observedAt);
        return activity;
    }

    private static PolicyDecision combinedDecision(
            LedgerPolicyEvaluator.PolicyEvaluation sourceEvaluation,
            LedgerPolicyEvaluator.PolicyEvaluation destinationEvaluation) {
        if (sourceEvaluation.decision() == PolicyDecision.HISTORICAL_BREACH_RECORDED
                || destinationEvaluation.decision() == PolicyDecision.HISTORICAL_BREACH_RECORDED) {
            return PolicyDecision.HISTORICAL_BREACH_RECORDED;
        }
        if (sourceEvaluation.decision() == PolicyDecision.CONFIRMED_BREACH
                || destinationEvaluation.decision() == PolicyDecision.CONFIRMED_BREACH) {
            return PolicyDecision.CONFIRMED_BREACH;
        }
        return PolicyDecision.ALLOWED;
    }

    private ActivityResponse saveResult(
            UUID ownerUserAccountId, Activity activity, String hash, UUID clientRequestId, Instant observedAt) {
        entityManager.flush();
        var response = readRepository
                .findActivity(ownerUserAccountId, activity.getId())
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACTIVITY_NOT_FOUND));
        idempotencyStore.save(
                ownerUserAccountId,
                LedgerCommandScopes.TRANSFER,
                clientRequestId,
                hash,
                "ACTIVITY",
                activity.getId(),
                response,
                observedAt);
        return response;
    }

    private static void validateTransferShape(FinancialAccount source, FinancialAccount destination) {
        if (source.getId().equals(destination.getId())
                || source.getTrackingMode() != TrackingMode.FULL_LEDGER
                || destination.getTrackingMode() != TrackingMode.FULL_LEDGER
                || source.getAccountKind().isLiability()
                || destination.getAccountKind().isLiability()
                || !source.getAccountKind().isCashFundingCapable()
                || !destination.getAccountKind().isCashFundingCapable()
                || source.isArchived()
                || destination.isArchived()
                || !source.getCurrencyCode().equals(destination.getCurrencyCode())) {
            if (!source.getCurrencyCode().equals(destination.getCurrencyCode())) {
                throw new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED);
            }
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
