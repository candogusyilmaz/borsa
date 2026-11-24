package dev.canverse.stocks.service.marketupdater;

import dev.canverse.stocks.domain.entity.instrument.Instrument;
import dev.canverse.stocks.repository.InstrumentRepository;
import dev.canverse.stocks.repository.MarketRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketInstrumentSyncService {

    private final MarketQuoteFetcher quoteFetcher;
    private final MarketRepository marketRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketPriceSyncService priceSyncService;
    private final List<MarketInstrumentFactory> factories;
    private final JdbcTemplate jdbcTemplate;

    public void sync(String marketCode, int minQuotes) {
        var code = marketCode.toUpperCase(Locale.ROOT);
        var market = marketRepository.findByCode(code);
        if (market == null) {
            log.error("[{}] Market not found in DB, skipping instrument sync", code);
            return;
        }

        var factory = factories.stream().filter(f -> f.supports(code)).findFirst().orElse(null);
        if (factory == null) {
            log.warn("[{}] No MarketInstrumentFactory found, skipping instrument sync", code);
            return;
        }

        var quotes = quoteFetcher.fetchForDiscovery(code, minQuotes);
        if (quotes.isEmpty()) {
            return;
        }

        var existingSymbols =
                new HashSet<>(
                        jdbcTemplate.queryForList(
                                "SELECT symbol FROM instrument.instruments WHERE market_id = ?",
                                String.class,
                                market.getId()));

        var newInstruments =
                quotes.stream()
                        .filter(q -> !existingSymbols.contains(q.symbol()))
                        .map(q -> factory.create(q, market))
                        .toList();

        if (newInstruments.isEmpty()) {
            log.info("[{}] Instrument sync finished, no new symbols", code);
            return;
        }

        instrumentRepository.saveAll(newInstruments);
        priceSyncService.evictInstrumentCache(code);

        log.info(
                "[{}] Added {} new instruments: {}",
                code,
                newInstruments.size(),
                newInstruments.stream().map(Instrument::getSymbol).toList());
    }
}
