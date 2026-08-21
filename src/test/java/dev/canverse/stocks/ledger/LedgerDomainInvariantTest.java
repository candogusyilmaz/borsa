package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
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

    private static UUID id() {
        return UUID.randomUUID();
    }
}
