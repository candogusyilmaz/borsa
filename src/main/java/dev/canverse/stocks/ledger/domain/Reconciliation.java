package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Immutable evidence of one native-currency statement boundary comparison. */
@Entity
@Table(name = "reconciliation", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class Reconciliation {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID financialAccountId;
    private UUID cashPocketId;
    private String currencyCode;
    private String statementReference;
    private Instant statementOpeningAt;
    private Instant statementClosingAt;
    private BigDecimal statementOpeningBalance;
    private BigDecimal statementClosingBalance;
    private BigDecimal ledgerOpeningBalance;
    private BigDecimal ledgerClosingBalanceBeforeAdjustment;
    private BigDecimal periodNetPostedAmount;
    private BigDecimal closingDifference;
    private BigDecimal adjustmentAmount;
    private long periodPostingCount;
    private long totalPostingCountThroughClosing;

    @Enumerated(EnumType.STRING)
    private ReconciliationResolution resolution;

    private UUID adjustmentActivityId;
    private UUID supersedesReconciliationId;

    @Enumerated(EnumType.STRING)
    private SourceKind sourceKind;

    @Column(name = "adjustment_reason")
    private String adjustmentReason;

    private Instant createdAt;

    public static Reconciliation create(
            UUID id,
            UUID ownerUserAccountId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            String statementReference,
            Instant statementOpeningAt,
            Instant statementClosingAt,
            FinancialAmount statementOpeningBalance,
            FinancialAmount statementClosingBalance,
            FinancialAmount ledgerOpeningBalance,
            FinancialAmount ledgerClosingBalanceBeforeAdjustment,
            FinancialAmount periodNetPostedAmount,
            FinancialAmount closingDifference,
            FinancialAmount adjustmentAmount,
            long periodPostingCount,
            long totalPostingCountThroughClosing,
            ReconciliationResolution resolution,
            UUID adjustmentActivityId,
            UUID supersedesReconciliationId,
            String adjustmentReason,
            Instant createdAt) {
        if (periodPostingCount < 0 || totalPostingCountThroughClosing < 0) {
            throw new IllegalArgumentException("Posting counts must be non-negative");
        }
        var opening = Objects.requireNonNull(statementOpeningBalance, "statementOpeningBalance");
        var closing = Objects.requireNonNull(statementClosingBalance, "statementClosingBalance");
        var ledgerOpening = Objects.requireNonNull(ledgerOpeningBalance, "ledgerOpeningBalance");
        var ledgerClosing =
                Objects.requireNonNull(ledgerClosingBalanceBeforeAdjustment, "ledgerClosingBalanceBeforeAdjustment");
        var periodNet = Objects.requireNonNull(periodNetPostedAmount, "periodNetPostedAmount");
        var difference = Objects.requireNonNull(closingDifference, "closingDifference");
        if (!opening.equals(ledgerOpening)) {
            throw new IllegalArgumentException("Reconciliation statement opening does not match ledger opening");
        }
        if (!ledgerOpening.add(periodNet).equals(ledgerClosing)) {
            throw new IllegalArgumentException("Reconciliation period equation does not balance");
        }
        if (statementOpeningAt == null
                || statementClosingAt == null
                || !statementOpeningAt.isBefore(statementClosingAt)) {
            throw new IllegalArgumentException("Statement opening must precede closing");
        }
        var normalizedReference = requireBoundedText(statementReference, 200, "statementReference");
        var effectiveResolution = Objects.requireNonNull(resolution, "resolution");
        var normalizedReason = adjustmentReason == null ? null : adjustmentReason.trim();
        if (effectiveResolution == ReconciliationResolution.BALANCED) {
            if (!difference.isZero()
                    || adjustmentAmount != null
                    || adjustmentActivityId != null
                    || normalizedReason != null
                    || !ledgerClosing.equals(closing)) {
                throw new IllegalArgumentException("Balanced reconciliation has an invalid adjustment shape");
            }
        } else {
            var adjustment = Objects.requireNonNull(adjustmentAmount, "adjustmentAmount");
            if (difference.isZero()
                    || !adjustment.equals(difference)
                    || adjustmentActivityId == null
                    || normalizedReason == null
                    || normalizedReason.isBlank()
                    || normalizedReason.length() > 500
                    || !ledgerClosing.add(adjustment).equals(closing)) {
                throw new IllegalArgumentException("Adjusted reconciliation has an invalid adjustment shape");
            }
        }
        if (supersedesReconciliationId != null && supersedesReconciliationId.equals(id)) {
            throw new IllegalArgumentException("A reconciliation cannot supersede itself");
        }

        var reconciliation = new Reconciliation();
        reconciliation.id = Objects.requireNonNull(id, "id");
        reconciliation.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        reconciliation.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        reconciliation.cashPocketId = Objects.requireNonNull(cashPocketId, "cashPocketId");
        reconciliation.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        reconciliation.statementReference = normalizedReference;
        reconciliation.statementOpeningAt = Objects.requireNonNull(statementOpeningAt, "statementOpeningAt");
        reconciliation.statementClosingAt = Objects.requireNonNull(statementClosingAt, "statementClosingAt");
        reconciliation.statementOpeningBalance = opening.value();
        reconciliation.statementClosingBalance = closing.value();
        reconciliation.ledgerOpeningBalance = ledgerOpening.value();
        reconciliation.ledgerClosingBalanceBeforeAdjustment = ledgerClosing.value();
        reconciliation.periodNetPostedAmount = periodNet.value();
        reconciliation.closingDifference = difference.value();
        reconciliation.adjustmentAmount = adjustmentAmount == null ? null : adjustmentAmount.value();
        reconciliation.periodPostingCount = periodPostingCount;
        reconciliation.totalPostingCountThroughClosing = totalPostingCountThroughClosing;
        reconciliation.resolution = effectiveResolution;
        reconciliation.adjustmentActivityId = adjustmentActivityId;
        reconciliation.supersedesReconciliationId = supersedesReconciliationId;
        reconciliation.sourceKind = SourceKind.USER_ENTERED;
        reconciliation.adjustmentReason = normalizedReason;
        reconciliation.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return reconciliation;
    }

    private static String requireBoundedText(String value, int maxLength, String name) {
        var normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
        return normalized;
    }
}
