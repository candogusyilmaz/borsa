package dev.canverse.stocks.identity.application;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(String accessToken, Instant expiresAt) {

    public IssuedAccessToken {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
