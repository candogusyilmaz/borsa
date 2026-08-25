package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID clientEventId;
    private String operationScope;
    private long commandSequence;

    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    private RecordingMode recordingMode;

    private Instant effectiveAt;
    private Instant recordedAt;
    private Long economicSequence;

    @Enumerated(EnumType.STRING)
    private SourceKind sourceKind;

    @Enumerated(EnumType.STRING)
    private PolicyDecision policyDecision;

    private String correctionReason;
    private UUID reversesActivityId;
    private UUID supersedesActivityId;

    public static Activity openingBalance(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision) {
        requireHistoricalDecision(policyDecision);
        return create(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.OPENING_BALANCE,
                RecordingMode.HISTORICAL_FACT,
                effectiveAt,
                recordedAt,
                policyDecision,
                null,
                null,
                null);
    }

    public static Activity correctedOpeningBalance(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision,
            String correctionReason,
            UUID supersedesActivityId) {
        requireHistoricalDecision(policyDecision);
        return create(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.OPENING_BALANCE,
                RecordingMode.HISTORICAL_FACT,
                effectiveAt,
                recordedAt,
                policyDecision,
                requireReason(correctionReason),
                null,
                Objects.requireNonNull(supersedesActivityId, "supersedesActivityId"));
    }

    public static Activity cashDeposit(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision) {
        return cashActivity(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.CASH_DEPOSIT,
                recordingMode,
                effectiveAt,
                recordedAt,
                policyDecision);
    }

    public static Activity cashWithdrawal(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision) {
        return cashActivity(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.CASH_WITHDRAWAL,
                recordingMode,
                effectiveAt,
                recordedAt,
                policyDecision);
    }

    public static Activity ownedTransfer(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision) {
        return cashActivity(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.OWNED_TRANSFER,
                recordingMode,
                effectiveAt,
                recordedAt,
                policyDecision);
    }

    public static Activity reconciliationAdjustment(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision,
            String adjustmentReason) {
        requireHistoricalDecision(policyDecision);
        return create(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.RECONCILIATION_ADJUSTMENT,
                RecordingMode.HISTORICAL_FACT,
                effectiveAt,
                recordedAt,
                policyDecision,
                requireReason(adjustmentReason),
                null,
                null);
    }

    public static Activity reversal(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            Instant effectiveAt,
            Instant recordedAt,
            String correctionReason,
            UUID reversesActivityId) {
        return create(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                ActivityType.REVERSAL,
                RecordingMode.HISTORICAL_FACT,
                effectiveAt,
                recordedAt,
                PolicyDecision.NOT_APPLICABLE,
                requireReason(correctionReason),
                Objects.requireNonNull(reversesActivityId, "reversesActivityId"),
                null);
    }

    private static Activity cashActivity(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            ActivityType activityType,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision) {
        if (recordingMode == null) {
            throw new NullPointerException("recordingMode");
        }
        if (recordingMode == RecordingMode.CURRENT_ACTION) {
            requireCurrentDecision(policyDecision);
        } else {
            requireHistoricalDecision(policyDecision);
        }
        return create(
                id,
                ownerUserAccountId,
                clientEventId,
                operationScope,
                commandSequence,
                activityType,
                recordingMode,
                effectiveAt,
                recordedAt,
                policyDecision,
                null,
                null,
                null);
    }

    private static Activity create(
            UUID id,
            UUID ownerUserAccountId,
            UUID clientEventId,
            String operationScope,
            long commandSequence,
            ActivityType activityType,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision,
            String correctionReason,
            UUID reversesActivityId,
            UUID supersedesActivityId) {
        var activity = new Activity();
        activity.id = Objects.requireNonNull(id, "id");
        activity.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        activity.clientEventId = Objects.requireNonNull(clientEventId, "clientEventId");
        activity.operationScope = Objects.requireNonNull(operationScope, "operationScope");
        if (commandSequence < 0) {
            throw new IllegalArgumentException("commandSequence must be non-negative");
        }
        activity.commandSequence = commandSequence;
        activity.activityType = Objects.requireNonNull(activityType, "activityType");
        activity.recordingMode = Objects.requireNonNull(recordingMode, "recordingMode");
        activity.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
        activity.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        activity.economicSequence = null;
        activity.sourceKind = SourceKind.USER_ENTERED;
        activity.policyDecision = Objects.requireNonNull(policyDecision, "policyDecision");
        activity.correctionReason = correctionReason;
        activity.reversesActivityId = reversesActivityId;
        activity.supersedesActivityId = supersedesActivityId;
        return activity;
    }

    private static void requireCurrentDecision(PolicyDecision policyDecision) {
        if (policyDecision != PolicyDecision.ALLOWED
                && policyDecision != PolicyDecision.CONFIRMED_BREACH
                && policyDecision != PolicyDecision.HISTORICAL_BREACH_RECORDED) {
            throw new IllegalArgumentException("Current action has an invalid policy decision");
        }
    }

    private static void requireHistoricalDecision(PolicyDecision policyDecision) {
        if (policyDecision != PolicyDecision.ALLOWED && policyDecision != PolicyDecision.HISTORICAL_BREACH_RECORDED) {
            throw new IllegalArgumentException("Historical fact has an invalid policy decision");
        }
    }

    private static String requireReason(String correctionReason) {
        var reason =
                Objects.requireNonNull(correctionReason, "correctionReason").trim();
        if (reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("correctionReason must contain 1 to 500 characters");
        }
        return reason;
    }
}
