package dev.canverse.stocks.investing.application.model;

import dev.canverse.stocks.investing.domain.PositionProjection;
import dev.canverse.stocks.investing.domain.SecurityPosting;
import dev.canverse.stocks.investing.domain.TradeReplayResult;
import java.util.UUID;

/** Candidate security reversal and projection replay prepared before the ledger writes reversal facts. */
public record TradeReversalPlan(
        UUID ownerUserAccountId,
        UUID accountId,
        UUID instrumentId,
        String currencyCode,
        SecurityPosting reversalPosting,
        PositionProjection existingProjection,
        TradeReplayResult replay
) {}
