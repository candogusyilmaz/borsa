package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.application.model.ReconciliationPreviewView;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PostingRole;
import dev.canverse.stocks.ledger.domain.Reconciliation;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationReadRepository;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationRepository;
import dev.canverse.stocks.ledger.web.request.ReconciliationAction;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
import dev.canverse.stocks.ledger.web.response.ReconciliationPreviewResponse;
import dev.canverse.stocks.ledger.web.response.ReconciliationResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationCommandService {

    private final EntityManager entityManager;
    private final LedgerAccountAccess accountAccess;
    private final LedgerCommandLockRepository commandLockRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationReadRepository reconciliationReadRepository;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository postingRepository;
    private final AccountBalanceProjectionRepository projectionRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;

    @Transactional(readOnly = true)
    public ReconciliationPreviewResponse preview(UUID ownerUserAccountId, UUID accountId, ReconciliationPreviewRequest request) {
        var statement = statementValues(request.statementReference(), request.statementOpeningAt(), request.statementClosingAt(),
                request.statementOpeningBalance(), request.statementClosingBalance());
        if (!statement.statementOpeningAt().isBefore(statement.statementClosingAt())) {
            throw new IllegalArgumentException("Statement opening must precede closing");
        }
        var observedAt = clock.instant();
        if (statement.statementOpeningAt().isAfter(observedAt) || statement.statementClosingAt().isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
        var comparison = comparison(ownerUserAccountId, accountId, statement);
        if (comparison.cashPocketId() == null || comparison.coverageStatus() == CoverageStatus.UNTRACKED) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
        requireCoverage(comparison);
        return ReconciliationPreviewResponse.from(comparison);
    }

    @Transactional
    public ReconciliationResponse commit(UUID ownerUserAccountId, UUID accountId, ReconciliationCommitRequest request) {
        var statement = statementValues(request.statementReference(), request.statementOpeningAt(), request.statementClosingAt(),
                request.statementOpeningBalance(), request.statementClosingBalance());
        var observedAt = clock.instant();
        if (statement.statementOpeningAt().isAfter(observedAt) || statement.statementClosingAt().isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
        var hash = reconciliationCommitFingerprint(accountId, statement, request.expectedBalanceVersion(), request.resolution(), request.adjustmentReason());
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.RECONCILIATION_COMMIT, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, LedgerCommandScopes.RECONCILIATION_COMMIT, hash,
                ReconciliationResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, accountId);
        if (account.getTrackingMode() != TrackingMode.FULL_LEDGER) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
        var projection = accountAccess.projectionForUpdate(ownerUserAccountId, accountId);
        if (projection.getVersion() != request.expectedBalanceVersion()) {
            throw new AppException(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
        }
        var comparison = comparison(ownerUserAccountId, accountId, statement);
        requireCoverage(comparison);
        requireOpeningContinuity(comparison);
        requireResolution(request.resolution().resolution(), comparison);
        var reconciliation = writeReconciliation(ownerUserAccountId, account, projection, statement, comparison, request.resolution().resolution(),
                request.adjustmentReason(), request.clientRequestId(), 0, null, observedAt);
        return saveResult(ownerUserAccountId, reconciliation, hash, request.clientRequestId(), observedAt, LedgerCommandScopes.RECONCILIATION_COMMIT);
    }

    @Transactional
    public ReconciliationResponse correct(UUID ownerUserAccountId, UUID reconciliationId, ReconciliationCorrectionRequest request) {
        var statement = statementValues(request.statementReference(), request.statementOpeningAt(), request.statementClosingAt(),
                request.statementOpeningBalance(), request.statementClosingBalance());
        var observedAt = clock.instant();
        if (statement.statementOpeningAt().isAfter(observedAt) || statement.statementClosingAt().isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
        var hash = reconciliationCorrectionFingerprint(reconciliationId, statement, request.expectedBalanceVersion(), request.resolution(),
                request.adjustmentReason(), request.correctionReason().trim());
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.RECONCILIATION_CORRECTION, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, LedgerCommandScopes.RECONCILIATION_CORRECTION, hash,
                ReconciliationResponse.class);
        if (replay != null) {
            return replay;
        }

        var target = reconciliationRepository.findOwnedForUpdate(ownerUserAccountId, reconciliationId)
                .orElseThrow(() -> new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND));
        if (reconciliationRepository.findDirectReplacement(ownerUserAccountId, reconciliationId).isPresent()) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_ALREADY_SUPERSEDED);
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, target.getFinancialAccountId());
        if (account.getTrackingMode() != TrackingMode.FULL_LEDGER) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
        var projection = accountAccess.projectionForUpdate(ownerUserAccountId, target.getFinancialAccountId());
        if (projection.getVersion() != request.expectedBalanceVersion()) {
            throw new AppException(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
        }
        var initialComparison = comparison(ownerUserAccountId, target.getFinancialAccountId(), statement);
        requireCoverage(initialComparison);

        var commandSequence = 0L;
        if (target.getAdjustmentActivityId() != null) {
            var originalAdjustment = activityRepository.findOwnedForUpdate(target.getAdjustmentActivityId(), ownerUserAccountId)
                    .orElseThrow(() -> new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND));
            if (originalAdjustment.getActivityType() != ActivityType.RECONCILIATION_ADJUSTMENT) {
                throw new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND);
            }
            reverseAdjustment(ownerUserAccountId, originalAdjustment, projection, request.clientRequestId(), request.correctionReason().trim(), observedAt,
                    commandSequence++);
            entityManager.flush();
        }

        var comparison = comparison(ownerUserAccountId, target.getFinancialAccountId(), statement);
        requireOpeningContinuity(comparison);
        requireResolution(request.resolution().resolution(), comparison);
        var reconciliation = writeReconciliation(ownerUserAccountId, account, projection, statement, comparison, request.resolution().resolution(),
                request.adjustmentReason(), request.clientRequestId(), commandSequence, target.getId(), observedAt);
        return saveResult(ownerUserAccountId, reconciliation, hash, request.clientRequestId(), observedAt, LedgerCommandScopes.RECONCILIATION_CORRECTION);
    }

    private Reconciliation writeReconciliation(UUID ownerUserAccountId, FinancialAccount account, AccountBalanceProjection projection,
            StatementValues statement, ReconciliationPreviewView comparison, ReconciliationResolution resolution, String adjustmentReason, UUID clientRequestId,
            long commandSequence, UUID supersedesReconciliationId, Instant observedAt) {
        UUID adjustmentActivityId = null;
        FinancialAmount adjustmentAmount = null;
        if (resolution == ReconciliationResolution.ADJUSTED) {
            var evaluation = LedgerPolicyEvaluator.evaluate(account, projection.balance(), comparison.closingDifference(), RecordingMode.HISTORICAL_FACT,
                    false);
            var activity = Activity.reconciliationAdjustment(idGenerator.next(), ownerUserAccountId, clientRequestId,
                    supersedesReconciliationId == null ? LedgerCommandScopes.RECONCILIATION_COMMIT : LedgerCommandScopes.RECONCILIATION_CORRECTION,
                    commandSequence, statement.statementClosingAt(), observedAt, evaluation.decision(), adjustmentReason.trim());
            activityRepository.save(activity);
            postingRepository.save(MoneyPosting.adjustment(idGenerator.next(), ownerUserAccountId, activity.getId(), account.getId(),
                    projection.getCashPocket().getId(), account.getCurrencyCode(), comparison.closingDifference(), observedAt));
            projection.apply(comparison.closingDifference(), observedAt, activity.getId(), observedAt);
            projectionRepository.save(projection);
            adjustmentActivityId = activity.getId();
            adjustmentAmount = comparison.closingDifference();
            entityManager.flush();
        }
        var totalPostingCount = comparison.totalPostingCountThroughClosing() + (resolution == ReconciliationResolution.ADJUSTED ? 1 : 0);
        var reconciliation = Reconciliation.create(idGenerator.next(), ownerUserAccountId, account.getId(), projection.getCashPocket().getId(),
                account.getCurrencyCode(), statement.statementReference(), statement.statementOpeningAt(), statement.statementClosingAt(),
                statement.statementOpeningBalance(), statement.statementClosingBalance(), comparison.ledgerOpeningBalance(),
                comparison.ledgerClosingBalanceBeforeAdjustment(), comparison.periodNetPostedAmount(), comparison.closingDifference(), adjustmentAmount,
                comparison.periodPostingCount(), totalPostingCount, resolution, adjustmentActivityId, supersedesReconciliationId,
                resolution == ReconciliationResolution.ADJUSTED ? adjustmentReason.trim() : null, observedAt);
        reconciliationRepository.save(reconciliation);
        return reconciliation;
    }

    private void reverseAdjustment(UUID ownerUserAccountId, Activity original, AccountBalanceProjection projection, UUID clientRequestId,
            String correctionReason, Instant observedAt, long commandSequence) {
        var reversal = Activity.reversal(idGenerator.next(), ownerUserAccountId, clientRequestId, LedgerCommandScopes.RECONCILIATION_CORRECTION,
                commandSequence, original.getEffectiveAt(), observedAt, correctionReason, original.getId());
        activityRepository.save(reversal);
        var postings = postingRepository.findOwnedActivity(ownerUserAccountId, original.getId());
        if (postings.size() != 1 || postings.getFirst().getPostingRole() != PostingRole.ADJUSTMENT) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND);
        }
        var originalPosting = postings.getFirst();
        if (!originalPosting.getFinancialAccountId().equals(projection.getFinancialAccount().getId()) ||
                !originalPosting.getCashPocketId().equals(projection.getCashPocket().getId()) ||
                !originalPosting.getCurrencyCode().equals(projection.getFinancialAccount().getCurrencyCode())) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND);
        }
        var inverse = FinancialAmount.of(originalPosting.getAmount()).negate();
        postingRepository.save(MoneyPosting.reversal(idGenerator.next(), ownerUserAccountId, reversal.getId(), originalPosting.getFinancialAccountId(),
                originalPosting.getCashPocketId(), originalPosting.getCurrencyCode(), inverse, observedAt));
        projection.apply(inverse, observedAt, reversal.getId(), observedAt);
    }

    private ReconciliationResponse saveResult(UUID ownerUserAccountId, Reconciliation reconciliation, String hash, UUID clientRequestId, Instant observedAt,
            String scope) {
        entityManager.flush();
        var response = reconciliationReadRepository.findDetail(ownerUserAccountId, reconciliation.getId())
                .orElseThrow(() -> new AppException(LedgerErrorCode.RECONCILIATION_NOT_FOUND));
        idempotencyStore.save(ownerUserAccountId, scope, clientRequestId, hash, "RECONCILIATION", reconciliation.getId(), response, observedAt);
        return response;
    }

    private ReconciliationPreviewView comparison(UUID ownerUserAccountId, UUID accountId, StatementValues statement) {
        return reconciliationReadRepository
                .findPreview(ownerUserAccountId, accountId, statement.statementReference(), statement.statementOpeningAt(), statement.statementClosingAt(),
                        statement.statementOpeningBalance(), statement.statementClosingBalance())
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    private static void requireCoverage(ReconciliationPreviewView comparison) {
        if (comparison.coverageFrom() == null || comparison.statementOpeningAt().isBefore(comparison.coverageFrom())) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_COVERAGE_GAP);
        }
    }

    private static void requireOpeningContinuity(ReconciliationPreviewView comparison) {
        if (!comparison.openingDifference().isZero()) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_OPENING_MISMATCH);
        }
    }

    private static void requireResolution(ReconciliationResolution resolution, ReconciliationPreviewView comparison) {
        var balanced = comparison.openingDifference().isZero() && comparison.closingDifference().isZero();
        var adjusted = comparison.openingDifference().isZero() && !comparison.closingDifference().isZero();
        if ((balanced && resolution != ReconciliationResolution.BALANCED) || (adjusted && resolution != ReconciliationResolution.ADJUSTED) ||
                (!balanced && !adjusted)) {
            throw new AppException(LedgerErrorCode.RECONCILIATION_RESOLUTION_REQUIRED);
        }
    }

    private String reconciliationCommitFingerprint(UUID accountId, StatementValues statement, Long expectedBalanceVersion, ReconciliationAction resolution,
            String adjustmentReason) {
        return fingerprint.hash(fingerprint.values("accountId", accountId.toString(), "statementReference", statement.statementReference(),
                "statementOpeningAt", statement.statementOpeningAt().toString(), "statementClosingAt", statement.statementClosingAt().toString(),
                "statementOpeningBalance", statement.statementOpeningBalance().canonical(), "statementClosingBalance",
                statement.statementClosingBalance().canonical(), "expectedBalanceVersion", expectedBalanceVersion, "resolution", resolution.name(),
                "adjustmentReason", adjustmentReason == null ? null : adjustmentReason.trim()));
    }

    private String reconciliationCorrectionFingerprint(UUID reconciliationId, StatementValues statement, Long expectedBalanceVersion,
            ReconciliationAction resolution, String adjustmentReason, String correctionReason) {
        return fingerprint.hash(fingerprint.values("reconciliationId", reconciliationId.toString(), "statementReference", statement.statementReference(),
                "statementOpeningAt", statement.statementOpeningAt().toString(), "statementClosingAt", statement.statementClosingAt().toString(),
                "statementOpeningBalance", statement.statementOpeningBalance().canonical(), "statementClosingBalance",
                statement.statementClosingBalance().canonical(), "expectedBalanceVersion", expectedBalanceVersion, "resolution", resolution.name(),
                "adjustmentReason", adjustmentReason == null ? null : adjustmentReason.trim(), "correctionReason", correctionReason));
    }

    private static StatementValues statementValues(String statementReference, Instant statementOpeningAt, Instant statementClosingAt,
            String statementOpeningBalance, String statementClosingBalance) {
        var opening = Objects.requireNonNull(statementOpeningAt, "statementOpeningAt");
        var closing = Objects.requireNonNull(statementClosingAt, "statementClosingAt");
        return new StatementValues(Objects.requireNonNull(statementReference, "statementReference").trim(), opening, closing,
                LedgerAmountParser.exact(statementOpeningBalance, "statementOpeningBalance"),
                LedgerAmountParser.exact(statementClosingBalance, "statementClosingBalance"));
    }

    private record StatementValues(String statementReference, Instant statementOpeningAt, Instant statementClosingAt, FinancialAmount statementOpeningBalance,
            FinancialAmount statementClosingBalance) {}
}
