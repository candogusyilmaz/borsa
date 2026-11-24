package dev.canverse.stocks.service.marketupdater;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MarketQuoteFetcher {

    private final Map<String, List<MarketQuoteProvider>> providersByMarket;

    public MarketQuoteFetcher(List<MarketQuoteProvider> providers) {
        this.providersByMarket =
                providers.stream()
                        .collect(Collectors.groupingBy(p -> normalize(p.getMarketCode())));
    }

    public List<MarketQuote> fetchForPricing(
            String marketCode, int minQuotes, Set<String> requiredSymbols) {
        var code = normalize(marketCode);
        var candidates = providersByMarket.getOrDefault(code, List.of());

        if (candidates.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "market_providers_missing")
                    .addKeyValue("market", code)
                    .log("No quote providers registered");
            return List.of();
        }

        var merged = new LinkedHashMap<String, MarketQuote>();

        for (var provider : candidates) {
            long startNanos = System.nanoTime();
            try {
                var quotes = deduplicateBySymbol(provider.fetchQuotes());
                int added = 0;

                for (var quote : quotes) {
                    if (merged.putIfAbsent(quote.symbol(), quote) == null) {
                        added++;
                    }
                }

                var missingRequired = findMissing(requiredSymbols, merged);
                boolean enoughQuotes = merged.size() >= minQuotes;

                if (enoughQuotes && missingRequired.isEmpty()) {
                    log.atDebug()
                            .addKeyValue("event", "market_provider_fetched")
                            .addKeyValue("market", code)
                            .addKeyValue("provider", provider.getName())
                            .addKeyValue("addedCount", added)
                            .addKeyValue("quoteCount", merged.size())
                            .addKeyValue("durationMs", elapsedMs(startNanos))
                            .log("Pricing quotes fetched");
                    return List.copyOf(merged.values());
                }

                log.atWarn()
                        .addKeyValue("event", "market_provider_fallback")
                        .addKeyValue("market", code)
                        .addKeyValue("provider", provider.getName())
                        .addKeyValue("quoteCount", merged.size())
                        .addKeyValue("minQuotes", minQuotes)
                        .addKeyValue("missingRequiredCount", missingRequired.size())
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Provider quote coverage incomplete; trying fallback");
            } catch (Exception ex) {
                log.atWarn()
                        .addKeyValue("event", "market_provider_failed")
                        .addKeyValue("market", code)
                        .addKeyValue("provider", provider.getName())
                        .addKeyValue("exception", ex.getClass().getSimpleName())
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Quote provider failed; trying next provider");
            }
        }

        if (merged.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "market_providers_exhausted")
                    .addKeyValue("market", code)
                    .log("All quote providers failed");
            return List.of();
        }

        log.atWarn()
                .addKeyValue("event", "market_data_partial_coverage")
                .addKeyValue("market", code)
                .addKeyValue("quoteCount", merged.size())
                .addKeyValue("missingRequiredCount", findMissing(requiredSymbols, merged).size())
                .log("Quote fetch finished with partial coverage");
        return List.copyOf(merged.values());
    }

    public List<MarketQuote> fetchForDiscovery(String marketCode, int minQuotes) {
        var code = normalize(marketCode);
        var candidates =
                providersByMarket.getOrDefault(code, List.of()).stream()
                        .filter(MarketQuoteProvider::supportsDiscovery)
                        .toList();

        if (candidates.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "market_providers_missing")
                    .addKeyValue("market", code)
                    .log("No discovery-capable quote providers registered");
            return List.of();
        }

        for (var provider : candidates) {
            long startNanos = System.nanoTime();
            try {
                var quotes = deduplicateBySymbol(provider.fetchQuotes());

                if (quotes.size() >= minQuotes) {
                    log.atDebug()
                            .addKeyValue("event", "market_provider_fetched")
                            .addKeyValue("market", code)
                            .addKeyValue("provider", provider.getName())
                            .addKeyValue("quoteCount", quotes.size())
                            .addKeyValue("durationMs", elapsedMs(startNanos))
                            .log("Discovery quotes fetched");
                    return quotes;
                }

                log.atWarn()
                        .addKeyValue("event", "market_provider_fallback")
                        .addKeyValue("market", code)
                        .addKeyValue("provider", provider.getName())
                        .addKeyValue("quoteCount", quotes.size())
                        .addKeyValue("minQuotes", minQuotes)
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Discovery provider returned insufficient quotes; trying fallback");
            } catch (Exception ex) {
                log.atWarn()
                        .addKeyValue("event", "market_provider_failed")
                        .addKeyValue("market", code)
                        .addKeyValue("provider", provider.getName())
                        .addKeyValue("exception", ex.getClass().getSimpleName())
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Discovery provider failed; trying next provider");
            }
        }

        log.atWarn()
                .addKeyValue("event", "market_providers_exhausted")
                .addKeyValue("market", code)
                .log("All discovery providers failed");
        return List.of();
    }

    private Set<String> findMissing(Set<String> requiredSymbols, Map<String, MarketQuote> merged) {
        if (requiredSymbols == null || requiredSymbols.isEmpty()) {
            return Set.of();
        }

        return requiredSymbols.stream()
                .filter(symbol -> !merged.containsKey(symbol))
                .collect(Collectors.toSet());
    }

    private List<MarketQuote> deduplicateBySymbol(List<MarketQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return List.of();
        }

        return List.copyOf(
                quotes.stream()
                        .collect(
                                Collectors.toMap(
                                        MarketQuote::symbol,
                                        Function.identity(),
                                        (first, second) -> first,
                                        LinkedHashMap::new))
                        .values());
    }

    private String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }
}
