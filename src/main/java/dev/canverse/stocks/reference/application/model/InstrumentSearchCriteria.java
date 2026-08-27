package dev.canverse.stocks.reference.application.model;

import dev.canverse.stocks.reference.domain.InstrumentType;
import java.util.UUID;

/** The complete owner-scoped instrument search requested by one application use case. */
public record InstrumentSearchCriteria(String query, UUID marketId, InstrumentType type, boolean includeInactive) {}
