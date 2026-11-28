package dev.canverse.stocks.service.marketupdater;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;

import dev.canverse.stocks.repository.InstrumentRepository;
import dev.canverse.stocks.repository.MarketRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketInstrumentSyncService {

    private static final String JOB_NAME = "market_instrument_sync";

    private final MarketQuoteFetcher quoteFetcher;
    private final MarketRepository marketRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketPriceSyncService priceSyncService;
    private final List<MarketInstrumentFactory> factories;
    private final JdbcTemplate jdbcTemplate;

    public void sync(String marketCode, int minQuotes) {
        long startNanos = System.nanoTime();
        var code = marketCode.toUpperCase(Locale.ROOT);

        try {
            var market = marketRepository.findByCode(code);
            if (market == null) {
                log.atError()
                    .addKeyValue("event", "scheduled_job_failed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("market", code)
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("reason", "market_not_found")
                    .addKeyValue("durationMs", elapsedMs(startNanos))
                    .log("Market instrument sync failed");
                return;
            }

            var factory = factories.stream().filter(f -> f.supports(code)).findFirst().orElse(null);
            if (factory == null) {
                log.atWarn()
                    .addKeyValue("event", "scheduled_job_completed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("market", code)
                    .addKeyValue("outcome", "skipped")
                    .addKeyValue("reason", "no_factory")
                    .addKeyValue("durationMs", elapsedMs(startNanos))
                    .log("Market instrument sync skipped");
                return;
            }

            var quotes = quoteFetcher.fetchForDiscovery(code, minQuotes);
            if (quotes.isEmpty()) {
                log.atError()
                    .addKeyValue("event", "scheduled_job_failed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("market", code)
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("reason", "no_quotes")
                    .addKeyValue("durationMs", elapsedMs(startNanos))
                    .log("Market instrument sync failed");
                return;
            }

            var existingSymbols = new HashSet<>(
                jdbcTemplate.queryForList("SELECT symbol FROM instrument.instruments WHERE market_id = ?", String.class, market.getId()));

            var newInstruments = quotes.stream()
                .filter(q -> !existingSymbols.contains(q.symbol()))
                .map(q -> factory.create(q, market))
                .toList();

            if (!newInstruments.isEmpty()) {
                instrumentRepository.saveAll(newInstruments);
                priceSyncService.evictInstrumentCache(code);
            }

            log.atInfo()
                .addKeyValue("event", "scheduled_job_completed")
                .addKeyValue("job", JOB_NAME)
                .addKeyValue("market", code)
                .addKeyValue("processedCount", quotes.size())
                .addKeyValue("succeededCount", newInstruments.size())
                .addKeyValue("failedCount", 0)
                .addKeyValue("durationMs", elapsedMs(startNanos))
                .addKeyValue("outcome", "success")
                .log("Market instrument sync completed");
        } catch (Exception ex) {
            log.atError()
                .addKeyValue("event", "scheduled_job_failed")
                .addKeyValue("job", JOB_NAME)
                .addKeyValue("market", code)
                .addKeyValue("outcome", "failed")
                .addKeyValue("durationMs", elapsedMs(startNanos))
                .setCause(ex)
                .log("Market instrument sync failed unexpectedly");
            throw ex;
        }
    }
}
