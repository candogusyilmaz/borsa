package dev.canverse.stocks.reference.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical three-letter currency identity; it does not contain an exchange rate. */
public record CurrencyCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}");

    public CurrencyCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Currency code must be canonical uppercase alpha-3");
        }
    }

    public static CurrencyCode of(String value) {
        return new CurrencyCode(value);
    }

    public String code() {
        return value;
    }
}
