package dev.canverse.stocks.investing.application.model;

import java.time.Instant;
import java.util.UUID;

public record PortfolioSummaryView(UUID id, String name, int accountCount, long version, Instant createdAt, Instant updatedAt, Instant archivedAt) {}
