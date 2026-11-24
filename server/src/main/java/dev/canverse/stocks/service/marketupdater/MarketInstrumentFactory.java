package dev.canverse.stocks.service.marketupdater;

import dev.canverse.stocks.domain.entity.instrument.Instrument;
import dev.canverse.stocks.domain.entity.instrument.Market;

public interface MarketInstrumentFactory {
    boolean supports(String marketCode);

    Instrument create(MarketQuote quote, Market market);
}
