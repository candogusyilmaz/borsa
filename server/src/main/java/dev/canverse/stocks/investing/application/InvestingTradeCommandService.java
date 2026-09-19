package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.application.model.TradeReversalPlan;
import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.PositionProjection;
import dev.canverse.stocks.investing.domain.SecurityPosting;
import dev.canverse.stocks.investing.domain.TradeEvent;
import dev.canverse.stocks.investing.domain.TradeReplayResult;
import dev.canverse.stocks.investing.domain.TradeSettlement;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.domain.WeightedAverageEconomicV1;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.InvestingReadRepository;
import dev.canverse.stocks.investing.infrastructure.PositionProjectionRepository;
import dev.canverse.stocks.investing.infrastructure.SecurityPostingRepository;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.investing.web.response.TradePreviewResponse;
import dev.canverse.stocks.investing.web.response.TradeResponse;
import dev.canverse.stocks.ledger.application.LedgerAccountAccess;
import dev.canverse.stocks.ledger.application.LedgerIdempotencyStore;
import dev.canverse.stocks.ledger.application.LedgerPolicyEvaluator;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.domain.Instrument;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.infrastructure.CurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestingTradeCommandService {

    private static final String OPERATION_SCOPE = "investing.trade";

    private final EntityManager entityManager;
    private final LedgerAccountAccess accountAccess;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository moneyPostingRepository;
    private final SecurityPostingRepository securityPostingRepository;
    private final PositionProjectionRepository positionProjectionRepository;
    private final InstrumentRepository instrumentRepository;
    private final CurrencyRepository currencyRepository;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final InvestingReadRepository investingReadRepository;
    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;

    @Transactional(readOnly = true)
    public TradePreviewResponse preview(UUID ownerUserAccountId, TradePreviewRequest request) {
        var input = parse(request.accountId(), request.instrumentId(), request.side(), request.quantity(), request.unitPrice(), request.commissionAmount(),
                request.recordingMode(), request.effectiveAt(), request.economicSequence(), request.confirmPolicyBreach());
        var observedAt = clock.instant();
        requireNotFuture(input.effectiveAt(), observedAt);
        var account = accountAccess.owned(ownerUserAccountId, input.accountId());
        requireEligibleAccount(account);
        var instrument = visibleInstrument(ownerUserAccountId, input.instrumentId(), false);
        validateInstrument(instrument, account, input);
        var currency = currencyRepository.findById(account.getCurrencyCode()).orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED));
        var settlement = input.settle(currency.getMinorUnit());
        var cashProjection = accountAccess.projection(ownerUserAccountId, account.getId());
        var position = positionProjectionRepository.findOwned(ownerUserAccountId, account.getId(), instrument.getId()).orElse(null);
        var positionVersion = position == null ? 0 : position.getVersion();
        var events = investingReadRepository.findPositionEvents(ownerUserAccountId, account.getId(), instrument.getId());
        var before = WeightedAverageEconomicV1.replay(events);
        var candidateActivityId = idGenerator.next();
        var after = WeightedAverageEconomicV1
                .replay(withCandidate(events, TradeEvent.trade(candidateActivityId, settlement, input.effectiveAt(), input.economicSequence())));
        var cashBalanceAfter = exactBalanceAfter(cashProjection.balance(), settlement.cashDelta());
        var evaluation = LedgerPolicyEvaluator.evaluate(account, cashProjection.balance(), settlement.cashDelta(), input.recordingMode(),
                input.confirmPolicyBreach());
        var disposal = after.disposals().get(candidateActivityId);

        return new TradePreviewResponse(account.getId(), instrument.getId(), instrument.getSymbol(), input.side(), input.quantity().canonical(),
                input.unitPrice().canonical(), input.commissionAmount().canonical(), settlement.grossAmount().canonical(), account.getCurrencyCode(),
                input.recordingMode(), input.effectiveAt(), input.economicSequence(), settlement.cashDelta().canonical(), cashProjection.balance().canonical(),
                cashBalanceAfter.canonical(), evaluation.decision(), evaluation.allowed(), before.state().quantity().canonical(),
                after.state().quantity().canonical(), before.state().remainingBasis().canonical(), after.state().remainingBasis().canonical(),
                before.state().realizedEconomicPnl().canonical(), after.state().realizedEconomicPnl().canonical(),
                disposal == null ? null : disposal.allocatedBasis().canonical(), disposal == null ? null : disposal.realizedEconomicPnl().canonical(),
                CalculationPolicy.WEIGHTED_AVERAGE_ECONOMIC_V1, cashProjection.getVersion(), positionVersion);
    }

    @Transactional
    public TradeResponse commit(UUID ownerUserAccountId, TradeCommitRequest request) {
        var input = parse(request.accountId(), request.instrumentId(), request.side(), request.quantity(), request.unitPrice(), request.commissionAmount(),
                request.recordingMode(), request.effectiveAt(), request.economicSequence(), request.confirmPolicyBreach());
        var observedAt = clock.instant();
        requireNotFuture(input.effectiveAt(), observedAt);
        var hash = tradeFingerprint(input, request.expectedCashBalanceVersion(), request.expectedPositionVersion());

        // Lock order: owner-scoped idempotency key, brokerage account, cash projection, position row, then visible reference instrument.
        commandLockRepository.lock(ownerUserAccountId, OPERATION_SCOPE, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, OPERATION_SCOPE, hash, TradeResponse.class);
        if (replay != null) {
            return replay;
        }

        var account = accountAccess.ownedForUpdate(ownerUserAccountId, input.accountId());
        requireEligibleAccount(account);
        var cashProjection = accountAccess.projectionForUpdate(ownerUserAccountId, account.getId());
        if (cashProjection.getVersion() != request.expectedCashBalanceVersion()) {
            throw new AppException(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
        }
        var position = positionProjectionRepository.findOwnedForUpdate(ownerUserAccountId, account.getId(), input.instrumentId()).orElse(null);
        var positionVersion = position == null ? 0 : position.getVersion();
        if (positionVersion != request.expectedPositionVersion()) {
            throw new AppException(InvestingErrorCode.POSITION_VERSION_CONFLICT);
        }
        var instrument = visibleInstrument(ownerUserAccountId, input.instrumentId(), true);
        validateInstrument(instrument, account, input);
        var currency = currencyRepository.findById(account.getCurrencyCode()).orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED));
        var settlement = input.settle(currency.getMinorUnit());
        exactBalanceAfter(cashProjection.balance(), settlement.cashDelta());
        var events = investingReadRepository.findPositionEvents(ownerUserAccountId, account.getId(), instrument.getId());
        var activityId = idGenerator.next();
        var candidate = TradeEvent.trade(activityId, settlement, input.effectiveAt(), input.economicSequence());
        var after = replayForRebuild(position, events, candidate, input.effectiveAt(), observedAt);
        var evaluation = LedgerPolicyEvaluator.evaluate(account, cashProjection.balance(), settlement.cashDelta(), input.recordingMode(),
                input.confirmPolicyBreach());
        if (!evaluation.allowed()) {
            throw new AppException(evaluation.errorCode());
        }

        var activity = createTradeActivity(ownerUserAccountId, request, input, evaluation.decision(), activityId, observedAt);
        activityRepository.save(activity);
        var cashPocketId = cashProjection.getCashPocket().getId();
        var grossPosting = input.side() == TradeSide.BUY
                ? MoneyPosting.tradePurchase(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId, account.getCurrencyCode(),
                        settlement.grossAmount(), observedAt)
                : MoneyPosting.tradeProceeds(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId, account.getCurrencyCode(),
                        settlement.grossAmount(), observedAt);
        moneyPostingRepository.save(grossPosting);
        if (!settlement.commissionAmount().isZero()) {
            moneyPostingRepository.save(MoneyPosting.fee(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId,
                    account.getCurrencyCode(), settlement.commissionAmount(), observedAt));
        }
        var securityPosting = input.side() == TradeSide.BUY
                ? SecurityPosting.buy(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), instrument.getId(), account.getCurrencyCode(),
                        settlement, input.effectiveAt(), input.economicSequence(), observedAt)
                : SecurityPosting.sell(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), instrument.getId(), account.getCurrencyCode(),
                        settlement, input.effectiveAt(), input.economicSequence(), observedAt);
        securityPostingRepository.save(securityPosting);
        cashProjection.apply(settlement.cashDelta(), observedAt, activityId, observedAt);
        rebuildPosition(ownerUserAccountId, account, instrument, position, after, observedAt);

        entityManager.flush();
        var response = investingReadRepository.findTrade(ownerUserAccountId, activityId).map(TradeResponse::from)
                .orElseThrow(() -> new AppException(InvestingErrorCode.TRADE_NOT_FOUND));
        idempotencyStore.save(ownerUserAccountId, OPERATION_SCOPE, request.clientRequestId(), hash, "TRADE", activityId, response, observedAt);
        return response;
    }

    @Transactional
    public TradeReversalPlan prepareTradeReversal(UUID ownerUserAccountId, Activity original, UUID reversalActivityId, Instant observedAt) {
        var originalPosting = securityPostingRepository.findOwnedActivity(ownerUserAccountId, original.getId())
                .orElseThrow(() -> new AppException(InvestingErrorCode.TRADE_NOT_FOUND));
        var economicSequence = Objects.requireNonNull(original.getEconomicSequence(), "trade economic sequence");
        var position = positionProjectionRepository
                .findOwnedForUpdate(ownerUserAccountId, originalPosting.getFinancialAccountId(), originalPosting.getInstrumentId()).orElse(null);
        var events = investingReadRepository.findPositionEvents(ownerUserAccountId, originalPosting.getFinancialAccountId(), originalPosting.getInstrumentId());
        var candidate = TradeEvent.reversal(reversalActivityId, original.getId(), original.getEffectiveAt(), economicSequence);
        var replay = replayForRebuild(position, events, candidate, original.getEffectiveAt(), observedAt);
        var reversalPosting = SecurityPosting.reversal(idGenerator.next(), ownerUserAccountId, reversalActivityId, originalPosting, observedAt);
        return new TradeReversalPlan(ownerUserAccountId, originalPosting.getFinancialAccountId(), originalPosting.getInstrumentId(),
                originalPosting.getTradeCurrencyCode(), reversalPosting, position, replay);
    }

    @Transactional
    public void persistTradeReversal(TradeReversalPlan plan, Instant observedAt) {
        securityPostingRepository.save(plan.reversalPosting());
        if (plan.existingProjection() == null) {
            positionProjectionRepository.save(PositionProjection.create(idGenerator.next(), plan.ownerUserAccountId(), plan.accountId(), plan.instrumentId(),
                    plan.currencyCode(), plan.replay(), observedAt));
        } else {
            plan.existingProjection().completeRebuild(plan.replay(), observedAt);
        }
    }

    private Activity createTradeActivity(UUID ownerUserAccountId, TradeCommitRequest request, TradeInputs input, PolicyDecision decision, UUID activityId,
            Instant observedAt) {
        return input.side() == TradeSide.BUY
                ? Activity.securityBuy(activityId, ownerUserAccountId, request.clientRequestId(), OPERATION_SCOPE, 0, input.recordingMode(),
                        input.effectiveAt(), observedAt, input.economicSequence(), decision)
                : Activity.securitySell(activityId, ownerUserAccountId, request.clientRequestId(), OPERATION_SCOPE, 0, input.recordingMode(),
                        input.effectiveAt(), observedAt, input.economicSequence(), decision);
    }

    private void rebuildPosition(UUID ownerUserAccountId, FinancialAccount account, Instrument instrument, PositionProjection position,
            TradeReplayResult replay, Instant observedAt) {
        if (position == null) {
            positionProjectionRepository.save(PositionProjection.create(idGenerator.next(), ownerUserAccountId, account.getId(), instrument.getId(),
                    account.getCurrencyCode(), replay, observedAt));
        } else {
            position.completeRebuild(replay, observedAt);
        }
    }

    private static TradeReplayResult replayForRebuild(PositionProjection position, List<TradeEvent> events, TradeEvent candidate, Instant invalidatedFrom,
            Instant startedAt) {
        if (position != null) {
            position.markStale(invalidatedFrom, startedAt);
            position.beginRebuild(startedAt);
        }

        try {
            return WeightedAverageEconomicV1.replay(withCandidate(events, candidate));
        } catch (AppException exception) {
            if (position != null) {
                position.failRebuild(startedAt);
            }
            throw exception;
        }
    }

    private Instrument visibleInstrument(UUID ownerUserAccountId, UUID instrumentId, boolean lockForTrade) {
        if (lockForTrade) {
            jdbcClient.sql("""
                    SELECT id
                    FROM reference.instrument
                    WHERE id = :instrumentId
                      AND (owner_user_account_id IS NULL OR owner_user_account_id = :ownerUserAccountId)
                    FOR UPDATE
                    """).param("instrumentId", instrumentId).param("ownerUserAccountId", ownerUserAccountId).query(UUID.class).optional()
                    .orElseThrow(() -> new AppException(InvestingErrorCode.UNSUPPORTED_INSTRUMENT));
        }
        return instrumentRepository.findVisibleById(instrumentId, ownerUserAccountId)
                .orElseThrow(() -> new AppException(InvestingErrorCode.UNSUPPORTED_INSTRUMENT));
    }

    private static void requireEligibleAccount(FinancialAccount account) {
        if (account.isArchived()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ARCHIVED);
        }
        if (account.getAccountKind() != AccountKind.BROKERAGE || account.getTrackingMode() != TrackingMode.FULL_LEDGER) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
    }

    private static void validateInstrument(Instrument instrument, FinancialAccount account, TradeInputs input) {
        if (instrument.getInstrumentType() != InstrumentType.EQUITY && instrument.getInstrumentType() != InstrumentType.ETF) {
            throw new AppException(InvestingErrorCode.UNSUPPORTED_INSTRUMENT);
        }
        if (!instrument.getQuotationCurrencyCode().equals(account.getCurrencyCode())) {
            throw new AppException(InvestingErrorCode.TRADE_CURRENCY_MISMATCH);
        }
        if (input.side() == TradeSide.BUY && input.recordingMode() == RecordingMode.CURRENT_ACTION && !instrument.isActive()) {
            throw new AppException(InvestingErrorCode.UNSUPPORTED_INSTRUMENT);
        }
    }

    private static void requireNotFuture(Instant effectiveAt, Instant observedAt) {
        if (effectiveAt.isAfter(observedAt)) {
            throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }
    }

    private static FinancialAmount exactBalanceAfter(FinancialAmount balance, FinancialAmount cashDelta) {
        try {
            return balance.add(cashDelta);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION, exception);
        }
    }

    private String tradeFingerprint(TradeInputs input, long expectedCashBalanceVersion, long expectedPositionVersion) {
        return fingerprint.hash(fingerprint.values("accountId", input.accountId().toString(), "instrumentId", input.instrumentId().toString(), "side",
                input.side().name(), "quantity", input.quantity().canonical(), "unitPrice", input.unitPrice().canonical(), "commissionAmount",
                input.commissionAmount().canonical(), "recordingMode", input.recordingMode().name(), "effectiveAt", input.effectiveAt().toString(),
                "economicSequence", input.economicSequence(), "confirmPolicyBreach", input.confirmPolicyBreach(), "expectedCashBalanceVersion",
                expectedCashBalanceVersion, "expectedPositionVersion", expectedPositionVersion));
    }

    private static List<TradeEvent> withCandidate(List<TradeEvent> events, TradeEvent candidate) {
        var result = new ArrayList<TradeEvent>(events.size() + 1);
        result.addAll(events);
        result.add(candidate);
        return result;
    }

    private static TradeInputs parse(UUID accountId, UUID instrumentId, TradeSide side, String quantity, String unitPrice, String commissionAmount,
            RecordingMode recordingMode, Instant effectiveAt, Long economicSequence, boolean confirmPolicyBreach) {
        return new TradeInputs(Objects.requireNonNull(accountId, "accountId"), Objects.requireNonNull(instrumentId, "instrumentId"),
                Objects.requireNonNull(side, "side"), positive(quantity, "quantity"), positive(unitPrice, "unitPrice"),
                nonNegative(commissionAmount, "commissionAmount"), Objects.requireNonNull(recordingMode, "recordingMode"),
                Objects.requireNonNull(effectiveAt, "effectiveAt").truncatedTo(ChronoUnit.MICROS), Objects.requireNonNull(economicSequence, "economicSequence"),
                confirmPolicyBreach);
    }

    private static FinancialAmount positive(String value, String field) {
        try {
            var amount = FinancialAmount.parse(value);
            if (amount.isPositive()) {
                return amount;
            }
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField(field, "error.fields.investing.invalid_amount", "The value must be a positive exact decimal.");
        }
        throw ValidationErrors.invalidField(field, "error.fields.investing.invalid_amount", "The value must be a positive exact decimal.");
    }

    private static FinancialAmount nonNegative(String value, String field) {
        try {
            var amount = FinancialAmount.parse(value);
            if (!amount.isNegative()) {
                return amount;
            }
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField(field, "error.fields.investing.invalid_amount", "The value must be a non-negative exact decimal.");
        }
        throw ValidationErrors.invalidField(field, "error.fields.investing.invalid_amount", "The value must be a non-negative exact decimal.");
    }

    private record TradeInputs(UUID accountId, UUID instrumentId, TradeSide side, FinancialAmount quantity, FinancialAmount unitPrice,
            FinancialAmount commissionAmount, RecordingMode recordingMode, Instant effectiveAt, long economicSequence, boolean confirmPolicyBreach) {

        TradeSettlement settle(int minorUnit) {
            return TradeSettlement.calculate(side, quantity, unitPrice, commissionAmount, minorUnit);
        }
    }
}
