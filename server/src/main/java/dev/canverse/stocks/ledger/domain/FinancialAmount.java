package dev.canverse.stocks.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** Exact native-currency amount used by the financial ledger. */
public final class FinancialAmount implements Comparable<FinancialAmount> {

    public static final int MAX_PRECISION = 38;
    public static final int MAX_SCALE = 18;
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private final BigDecimal value;

    private FinancialAmount(BigDecimal value) {
        this.value = normalize(Objects.requireNonNull(value, "value"));
    }

    public static FinancialAmount parse(String text) {
        Objects.requireNonNull(text, "text");
        if (!PLAIN_DECIMAL.matcher(text).matches() || text.startsWith("+")) {
            throw new IllegalArgumentException("Amount must be a plain decimal without a plus sign or exponent");
        }
        var decimalPoint = text.indexOf('.');
        if (decimalPoint >= 0 && text.length() - decimalPoint - 1 > MAX_SCALE) {
            throw new IllegalArgumentException("Amount has more than 18 fractional digits");
        }
        return new FinancialAmount(new BigDecimal(text));
    }

    public static FinancialAmount of(BigDecimal value) {
        return new FinancialAmount(value);
    }

    public static FinancialAmount zero() {
        return new FinancialAmount(BigDecimal.ZERO);
    }

    public BigDecimal value() {
        return value;
    }

    public String canonical() {
        return value.toPlainString();
    }

    public FinancialAmount add(FinancialAmount other) {
        return new FinancialAmount(value.add(Objects.requireNonNull(other, "other").value));
    }

    public FinancialAmount subtract(FinancialAmount other) {
        return new FinancialAmount(value.subtract(Objects.requireNonNull(other, "other").value));
    }

    public FinancialAmount negate() {
        return new FinancialAmount(value.negate());
    }

    public FinancialAmount abs() {
        return new FinancialAmount(value.abs());
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    @Override
    public int compareTo(FinancialAmount other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FinancialAmount amount && value.compareTo(amount.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return canonical();
    }

    private static BigDecimal normalize(BigDecimal input) {
        var normalized = input.signum() == 0 ? BigDecimal.ZERO : input.stripTrailingZeros();
        if (normalized.scale() > MAX_SCALE || normalized.precision() > MAX_PRECISION) {
            throw new IllegalArgumentException("Amount exceeds numeric(38,18)");
        }
        var integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > MAX_PRECISION - MAX_SCALE) {
            throw new IllegalArgumentException("Amount exceeds 20 integer digits");
        }
        return normalized;
    }
}
