package dev.canverse.stocks.reference.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical ISO-3166 alpha-2 identity; callers must supply the canonical form. */
public record CountryCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}");

    public CountryCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Country code must be canonical uppercase alpha-2");
        }
    }

    public static CountryCode of(String value) {
        return new CountryCode(value);
    }

    public String code() {
        return value;
    }
}
