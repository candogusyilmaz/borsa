package dev.canverse.stocks.reference.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Display symbol with a deterministic, separate Locale.ROOT search form. */
public record InstrumentSymbol(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/+-]*");

    public InstrumentSymbol {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        var normalized = value.toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > ManualInstrumentConstraints.MAX_SYMBOL_LENGTH ||
                normalized.length() > ManualInstrumentConstraints.MAX_SYMBOL_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Instrument symbol must contain 1 to " + ManualInstrumentConstraints.MAX_SYMBOL_LENGTH + " valid characters");
        }
    }

    public static InstrumentSymbol of(String value) {
        return new InstrumentSymbol(value);
    }

    public String normalized() {
        return value.toUpperCase(Locale.ROOT);
    }

    public String normalizedValue() {
        return normalized();
    }
}
