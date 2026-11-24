package dev.canverse.stocks.service.marketupdater;

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
            log.warn("[{}] No quote providers registered", code);
            return List.of();
        }

        var merged = new LinkedHashMap<String, MarketQuote>();

        for (var provider : candidates) {
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
                    log.info(
                            "[{}] Pricing quotes complete via {} (added {}, total {})",
                            code,
                            provider.getName(),
                            added,
                            merged.size());
                    return List.copyOf(merged.values());
                }

                log.warn(
                        "[{}] {} incomplete (total {}/{}, missing held symbols: {}), trying"
                                + " fallback to fill gaps",
                        code,
                        provider.getName(),
                        merged.size(),
                        minQuotes,
                        missingRequired);
            } catch (Exception ex) {
                log.warn(
                        "[{}] {} failed, trying next provider: {}",
                        code,
                        provider.getName(),
                        ex.getMessage());
            }
        }

        if (merged.isEmpty()) {
            log.error("[{}] All quote providers failed", code);
            return List.of();
        }

        log.warn(
                "[{}] Finished with partial coverage after all providers (total {}, still"
                        + " missing held: {})",
                code,
                merged.size(),
                findMissing(requiredSymbols, merged));
        return List.copyOf(merged.values());
    }

    public List<MarketQuote> fetchForDiscovery(String marketCode, int minQuotes) {
        var code = normalize(marketCode);
        var candidates =
                providersByMarket.getOrDefault(code, List.of()).stream()
                        .filter(MarketQuoteProvider::supportsDiscovery)
                        .toList();

        if (candidates.isEmpty()) {
            log.warn("[{}] No discovery-capable quote providers registered", code);
            return List.of();
        }

        for (var provider : candidates) {
            try {
                var quotes = deduplicateBySymbol(provider.fetchQuotes());

                if (quotes.size() >= minQuotes) {
                    log.info(
                            "[{}] Fetched {} discovery quotes via {}",
                            code,
                            quotes.size(),
                            provider.getName());
                    return quotes;
                }

                log.warn(
                        "[{}] {} returned insufficient discovery quotes ({}, expected at least"
                                + " {}), trying fallback",
                        code,
                        provider.getName(),
                        quotes.size(),
                        minQuotes);
            } catch (Exception ex) {
                log.warn(
                        "[{}] {} failed during discovery, trying next provider: {}",
                        code,
                        provider.getName(),
                        ex.getMessage());
            }
        }

        log.error("[{}] All discovery providers failed", code);
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
