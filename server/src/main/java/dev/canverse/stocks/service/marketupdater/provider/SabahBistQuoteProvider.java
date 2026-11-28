package dev.canverse.stocks.service.marketupdater.provider;

import dev.canverse.stocks.service.client.SabahClient;
import dev.canverse.stocks.service.marketupdater.MarketQuote;
import dev.canverse.stocks.service.marketupdater.MarketQuoteProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market-updater.providers.sabah.enabled", havingValue = "true", matchIfMissing = true)
public class SabahBistQuoteProvider implements MarketQuoteProvider {

    private final SabahClient sabahClient;

    @Override
    public String getMarketCode() {
        return "BIST";
    }

    @Override
    public String getName() {
        return "Sabah";
    }

    @Override
    public List<MarketQuote> fetchQuotes() {
        var response = sabahClient.fetchBistStocks();
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return List.of();
        }

        return response.data().stream().map(item -> {
            if (item.symbol() == null || item.price() == null || item.time() == null) {
                return null;
            }
            if (item.price().signum() <= 0) {
                return null;
            }

            var change = item.change() != null ? item.change() : BigDecimal.ZERO;
            var previousClose = item.price().subtract(change);
            if (previousClose.signum() <= 0) {
                return null;
            }

            var symbol = item.symbol().trim();
            if (symbol.isEmpty()) {
                return null;
            }

            return new MarketQuote(symbol, symbol, "TRY", item.price(), previousClose, item.time());
        }).filter(Objects::nonNull).toList();
    }
}
