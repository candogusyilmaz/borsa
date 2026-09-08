package dev.canverse.stocks.identity.infrastructure;

import java.util.UUID;

public record RefreshSessionOwnerProjection(UUID sessionId, UUID userAccountId) {}
