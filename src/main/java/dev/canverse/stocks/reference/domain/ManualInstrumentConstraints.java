package dev.canverse.stocks.reference.domain;

import java.util.Locale;

/**
 * Limits shared by manual-instrument requests and their application invariants.
 */
public final class ManualInstrumentConstraints {

    public static final int MAX_ALIASES_PER_INSTRUMENT = 32;
    public static final int MAX_SYMBOL_LENGTH = 32;
    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_ALIAS_VALUE_LENGTH = 128;

    private ManualInstrumentConstraints() {}

    public static boolean fitsDisplayAndNormalizedBounds(String value, int maximumLength) {
        var display = value.trim();
        var normalized = display.toUpperCase(Locale.ROOT);
        return characterCount(display) >= 1 && characterCount(display) <= maximumLength && characterCount(normalized) <= maximumLength;
    }

    private static int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
