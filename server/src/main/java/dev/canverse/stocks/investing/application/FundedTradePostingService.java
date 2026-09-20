package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.domain.SecurityPosting;
import dev.canverse.stocks.investing.domain.TradeSettlement;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.infrastructure.SecurityPostingRepository;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persists the immutable ledger facts shared by manual and file-imported funded trades. */
@Component
@RequiredArgsConstructor
public class FundedTradePostingService {

    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository moneyPostingRepository;
    private final SecurityPostingRepository securityPostingRepository;
    private final IdGenerator idGenerator;

    public Activity post(UUID activityId, UUID ownerUserAccountId, UUID clientEventId, String operationScope, long commandSequence, RecordingMode recordingMode,
            Instant effectiveAt, Instant recordedAt, long economicSequence, PolicyDecision policyDecision, UUID sourceImportRowId, FinancialAccount account,
            UUID cashPocketId, UUID instrumentId, TradeSettlement settlement) {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(settlement, "settlement");
        Activity activity;
        if (sourceImportRowId == null) {
            activity = settlement.side() == TradeSide.BUY
                    ? Activity.securityBuy(activityId, ownerUserAccountId, clientEventId, operationScope, commandSequence, recordingMode, effectiveAt,
                            recordedAt, economicSequence, policyDecision)
                    : Activity.securitySell(activityId, ownerUserAccountId, clientEventId, operationScope, commandSequence, recordingMode, effectiveAt,
                            recordedAt, economicSequence, policyDecision);
        } else {
            activity = settlement.side() == TradeSide.BUY
                    ? Activity.importedSecurityBuy(activityId, ownerUserAccountId, clientEventId, operationScope, commandSequence, effectiveAt, recordedAt,
                            economicSequence, policyDecision, sourceImportRowId)
                    : Activity.importedSecuritySell(activityId, ownerUserAccountId, clientEventId, operationScope, commandSequence, effectiveAt, recordedAt,
                            economicSequence, policyDecision, sourceImportRowId);
        }
        activityRepository.save(activity);

        var grossPosting = settlement.side() == TradeSide.BUY
                ? MoneyPosting.tradePurchase(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId, account.getCurrencyCode(),
                        settlement.grossAmount(), recordedAt)
                : MoneyPosting.tradeProceeds(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId, account.getCurrencyCode(),
                        settlement.grossAmount(), recordedAt);
        moneyPostingRepository.save(grossPosting);
        if (!settlement.commissionAmount().isZero()) {
            moneyPostingRepository.save(MoneyPosting.fee(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), cashPocketId,
                    account.getCurrencyCode(), settlement.commissionAmount(), recordedAt));
        }
        var securityPosting = settlement.side() == TradeSide.BUY
                ? SecurityPosting.buy(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), instrumentId, account.getCurrencyCode(), settlement,
                        effectiveAt, economicSequence, recordedAt)
                : SecurityPosting.sell(idGenerator.next(), ownerUserAccountId, activityId, account.getId(), instrumentId, account.getCurrencyCode(), settlement,
                        effectiveAt, economicSequence, recordedAt);
        securityPostingRepository.save(securityPosting);
        return activity;
    }
}
