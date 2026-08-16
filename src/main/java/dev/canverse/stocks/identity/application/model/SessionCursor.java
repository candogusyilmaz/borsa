package dev.canverse.stocks.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record SessionCursor(Instant createdAt, UUID familyId) {}
