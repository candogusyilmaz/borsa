package dev.canverse.stocks.reference.application;

import java.util.Objects;
import java.util.UUID;

public record InstrumentSearchCursor(
        String filterDigest, String symbolNormalized, String marketCodeNormalized, UUID instrumentId) {

    public InstrumentSearchCursor {
        Objects.requireNonNull(filterDigest, "filterDigest");
        Objects.requireNonNull(symbolNormalized, "symbolNormalized");
        Objects.requireNonNull(marketCodeNormalized, "marketCodeNormalized");
        Objects.requireNonNull(instrumentId, "instrumentId");
    }
}
