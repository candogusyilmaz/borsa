package dev.canverse.stocks.identity.application.model;

import java.util.UUID;

public record AuthenticatedIdentity(UUID userAccountId, UUID sessionId) {}
