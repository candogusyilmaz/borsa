package dev.canverse.stocks.service.marketupdater;

import java.util.List;

public interface MarketQuoteProvider {
    String getMarketCode();

    String getName();

    List<MarketQuote> fetchQuotes();

    default boolean supportsDiscovery() {
        return false;
    }
}
