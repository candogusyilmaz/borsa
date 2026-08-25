package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.Reconciliation;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerDomainInvariantTest {

    private static final Instant TIME = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    void typedPostingFactoriesRejectInvalidSigns() {
        assertThatThrownBy(() ->
                        MoneyPosting.deposit(id(), id(), id(), id(), id(), "USD", FinancialAmount.parse("-1"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        MoneyPosting.withdrawal(id(), id(), id(), id(), id(), "USD", FinancialAmount.parse("1"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        MoneyPosting.transferSource(id(), id(), id(), id(), id(), "USD", FinancialAmount.zero(), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyPosting.transferDestination(
                        id(), id(), id(), id(), id(), "USD", FinancialAmount.zero(), TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typedActivityFactoriesRejectInvalidPolicyShapes() {
        assertThatThrownBy(() ->
                        Activity.openingBalance(id(), id(), id(), "test", 0, TIME, TIME, PolicyDecision.NOT_APPLICABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.cashDeposit(
                        id(),
                        id(),
                        id(),
                        "test",
                        0,
                        RecordingMode.CURRENT_ACTION,
                        TIME,
                        TIME,
                        PolicyDecision.NOT_APPLICABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.reversal(id(), id(), id(), "test", 0, TIME, TIME, "", id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciliationAndAdjustmentFactoriesPreserveExactEquationsAndSignedReality() {
        var balanced = Reconciliation.create(
                id(),
                id(),
                id(),
                id(),
                "USD",
                "Balanced",
                TIME.minusSeconds(60),
                TIME,
                FinancialAmount.parse("100.00"),
                FinancialAmount.parse("125.50"),
                FinancialAmount.parse("100"),
                FinancialAmount.parse("125.5"),
                FinancialAmount.parse("25.50"),
                FinancialAmount.zero(),
                null,
                1,
                2,
                ReconciliationResolution.BALANCED,
                null,
                null,
                null,
                TIME);
        assertThat(balanced.getClosingDifference()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> Reconciliation.create(
                        id(),
                        id(),
                        id(),
                        id(),
                        "USD",
                        "Opening mismatch",
                        TIME.minusSeconds(60),
                        TIME,
                        FinancialAmount.parse("99"),
                        FinancialAmount.parse("100"),
                        FinancialAmount.parse("100"),
                        FinancialAmount.parse("100"),
                        FinancialAmount.zero(),
                        FinancialAmount.zero(),
                        null,
                        0,
                        1,
                        ReconciliationResolution.BALANCED,
                        null,
                        null,
                        null,
                        TIME))
                .isInstanceOf(IllegalArgumentException.class);

        var adjustmentActivityId = id();
        var adjusted = Reconciliation.create(
                id(),
                id(),
                id(),
                id(),
                "USD",
                "Adjusted",
                TIME.minusSeconds(60),
                TIME,
                FinancialAmount.parse("100"),
                FinancialAmount.parse("120"),
                FinancialAmount.parse("100"),
                FinancialAmount.parse("125"),
                FinancialAmount.parse("25"),
                FinancialAmount.parse("-5"),
                FinancialAmount.parse("-5.00"),
                1,
                3,
                ReconciliationResolution.ADJUSTED,
                adjustmentActivityId,
                null,
                "Statement correction",
                TIME);
        assertThat(adjusted.getAdjustmentAmount()).isEqualByComparingTo("-5");
        var adjustmentPosting =
                MoneyPosting.adjustment(id(), id(), id(), id(), id(), "USD", FinancialAmount.parse("-5"), TIME);
        assertThat(adjustmentPosting.getAmount()).isEqualByComparingTo("-5");
        assertThatThrownBy(() ->
                        MoneyPosting.adjustment(id(), id(), id(), id(), id(), "USD", FinancialAmount.zero(), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.reconciliationAdjustment(
                        id(), id(), id(), "test", 0, TIME, TIME, PolicyDecision.NOT_APPLICABLE, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciliationAdjustmentReasonIsCanonicalAndBoundedAtTheDomainBoundary() {
        var activity = Activity.reconciliationAdjustment(
                id(), id(), id(), "test", 0, TIME, TIME, PolicyDecision.ALLOWED, "  padded reason  ");

        assertThat(activity.getCorrectionReason()).isEqualTo("padded reason");
        assertThatThrownBy(() -> Activity.reconciliationAdjustment(
                        id(), id(), id(), "test", 0, TIME, TIME, PolicyDecision.ALLOWED, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.reconciliationAdjustment(
                        id(), id(), id(), "test", 0, TIME, TIME, PolicyDecision.ALLOWED, "r".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UUID id() {
        return UUID.randomUUID();
    }
}
