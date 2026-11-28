package dev.canverse.stocks.service.marketupdater.provider;

import dev.canverse.stocks.domain.entity.instrument.Instrument;
import dev.canverse.stocks.domain.entity.instrument.Market;
import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import dev.canverse.stocks.service.marketupdater.MarketInstrumentFactory;
import dev.canverse.stocks.service.marketupdater.MarketQuote;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StockInstrumentFactory implements MarketInstrumentFactory {

    private static final Set<String> STOCK_MARKETS = Set.of("BIST", "NASDAQ", "NYSE");

    @Override
    public boolean supports(String marketCode) {
        return STOCK_MARKETS.contains(marketCode);
    }

    @Override
    public Instrument create(MarketQuote quote, Market market) {
        return new StockInstrument(quote.name(), quote.symbol(), market);
    }
}
