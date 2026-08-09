package dev.canverse.stocks.identity.application;

import java.util.Objects;

public record GeneratedRefreshToken(String rawToken, String hash) {

    public GeneratedRefreshToken {
        Objects.requireNonNull(rawToken, "rawToken");
        Objects.requireNonNull(hash, "hash");
    }
}
