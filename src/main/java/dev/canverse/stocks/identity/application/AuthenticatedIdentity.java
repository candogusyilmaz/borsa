package dev.canverse.stocks.identity.application;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedIdentity(UUID userAccountId, UUID sessionId) {

    public AuthenticatedIdentity {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
