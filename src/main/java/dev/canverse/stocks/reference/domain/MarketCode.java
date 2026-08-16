package dev.canverse.stocks.reference.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical application market code; market identity is not a generated numeric ID. */
public record MarketCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,31}");

    public MarketCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Market code must be canonical uppercase");
        }
    }

    public static MarketCode of(String value) {
        return new MarketCode(value);
    }

    public String code() {
        return value;
    }
}
