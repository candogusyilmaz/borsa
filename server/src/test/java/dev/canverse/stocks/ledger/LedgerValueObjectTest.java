package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LedgerValueObjectTest {

    @Test
    void canonicalizesExactPlainDecimalsAndEquivalentScales() {
        assertThat(FinancialAmount.parse("0.00").canonical()).isEqualTo("0");
        assertThat(FinancialAmount.parse("-10.5000").canonical()).isEqualTo("-10.5");
        assertThat(FinancialAmount.parse("1.230000000000000000")).isEqualTo(FinancialAmount.parse("1.23"));
        assertThat(FinancialAmount.parse("1.230000000000000000").hashCode()).isEqualTo(FinancialAmount.parse("1.23").hashCode());
    }

    @Test
    void rejectsNonPlainAndUnrepresentableDecimalsWithoutRounding() {
        assertThatThrownBy(() -> FinancialAmount.parse("1e2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialAmount.parse("+1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialAmount.parse("1,000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialAmount.parse("1.0000000000000000001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialAmount.of(new BigDecimal("100000000000000000000.00"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capabilityMatrixDistinguishesBrokerageHoldingsAndLiabilities() {
        assertThat(AccountKind.BROKERAGE.supportsHoldingsOnly()).isTrue();
        assertThat(AccountKind.CASH_CURRENT.supportsHoldingsOnly()).isFalse();
        assertThat(AccountKind.CASH_CURRENT.supportsNegativePolicy(NegativeBalancePolicy.AUTHORIZED_LIMIT)).isTrue();
        assertThat(AccountKind.CASH_SAVINGS.supportsNegativePolicy(NegativeBalancePolicy.AUTHORIZED_LIMIT)).isFalse();
        assertThat(AccountKind.LOAN.isLiability()).isTrue();
        assertThat(AccountKind.LOAN.isCashFundingCapable()).isFalse();
    }
}
