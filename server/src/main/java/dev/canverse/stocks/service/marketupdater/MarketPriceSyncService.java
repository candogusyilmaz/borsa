package dev.canverse.stocks.service.marketupdater;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.canverse.stocks.domain.Calculator;

import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class MarketPriceSyncService {

    private static final String JOB_NAME = "market_price_sync";

    private final MarketQuoteFetcher quoteFetcher;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Cache<String, Map<String, Long>> instrumentCache;

    public MarketPriceSyncService(
            MarketQuoteFetcher quoteFetcher,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.quoteFetcher = quoteFetcher;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.instrumentCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).build();
    }

    public void sync(String marketCode, int minQuotes) {
        long startNanos = System.nanoTime();
        var code = marketCode.toUpperCase(Locale.ROOT);

        try {
            var instruments = instrumentCache.get(code, this::fetchInstruments);

            if (instruments == null || instruments.isEmpty()) {
                log.atWarn()
                        .addKeyValue("event", "market_data_update_completed")
                        .addKeyValue("job", JOB_NAME)
                        .addKeyValue("market", code)
                        .addKeyValue("outcome", "skipped")
                        .addKeyValue("reason", "no_instruments")
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Market price sync skipped");
                return;
            }

            var heldSymbols = fetchHeldSymbols(code);
            var quotes = quoteFetcher.fetchForPricing(code, minQuotes, heldSymbols);
            if (quotes.isEmpty()) {
                log.atError()
                        .addKeyValue("event", "market_data_update_failed")
                        .addKeyValue("job", JOB_NAME)
                        .addKeyValue("market", code)
                        .addKeyValue("outcome", "failed")
                        .addKeyValue("reason", "no_quotes")
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Market price sync failed");
                return;
            }

            var snapshots =
                    quotes.stream()
                            .map(
                                    q -> {
                                        var id = instruments.get(q.symbol());
                                        if (id == null) {
                                            return null;
                                        }

                                        return new Snapshot(
                                                q.currencyCode(),
                                                q.last(),
                                                q.previousClose(),
                                                Timestamp.from(q.updatedAt()),
                                                id);
                                    })
                            .filter(Objects::nonNull)
                            .toList();

            if (snapshots.isEmpty()) {
                log.atWarn()
                        .addKeyValue("event", "market_data_update_completed")
                        .addKeyValue("job", JOB_NAME)
                        .addKeyValue("market", code)
                        .addKeyValue("processedCount", quotes.size())
                        .addKeyValue("succeededCount", 0)
                        .addKeyValue("failedCount", quotes.size())
                        .addKeyValue("outcome", "skipped")
                        .addKeyValue("reason", "no_matching_instruments")
                        .addKeyValue("durationMs", elapsedMs(startNanos))
                        .log("Market price sync matched no instruments");
                return;
            }

            transactionTemplate.executeWithoutResult(status -> batchUpdateSnapshots(snapshots));

            log.atInfo()
                    .addKeyValue("event", "market_data_update_completed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("market", code)
                    .addKeyValue("processedCount", quotes.size())
                    .addKeyValue("succeededCount", snapshots.size())
                    .addKeyValue("failedCount", quotes.size() - snapshots.size())
                    .addKeyValue("durationMs", elapsedMs(startNanos))
                    .addKeyValue("outcome", "success")
                    .log("Market price sync completed");
        } catch (Exception ex) {
            log.atError()
                    .addKeyValue("event", "market_data_update_failed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("market", code)
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("durationMs", elapsedMs(startNanos))
                    .setCause(ex)
                    .log("Market price sync failed unexpectedly");
            throw ex;
        }
    }

    public void evictInstrumentCache(String marketCode) {
        instrumentCache.invalidate(marketCode.toUpperCase(Locale.ROOT));
    }

    private Map<String, Long> fetchInstruments(String marketCode) {
        return jdbcTemplate.query(
                """
                    SELECT i.symbol, i.id
                    FROM instrument.instruments i
                    JOIN instrument.markets m ON i.market_id = m.id
                    WHERE m.code = ?
                """,
                ps -> ps.setString(1, marketCode),
                rs -> {
                    var map = new HashMap<String, Long>();
                    while (rs.next()) {
                        map.put(rs.getString("symbol"), rs.getLong("id"));
                    }
                    return map;
                });
    }

    private Set<String> fetchHeldSymbols(String marketCode) {
        return new HashSet<>(
                jdbcTemplate.queryForList(
                        """
                            SELECT DISTINCT i.symbol
                            FROM portfolio.positions p
                            JOIN instrument.instruments i ON i.id = p.instrument_id
                            JOIN instrument.markets m ON m.id = i.market_id
                            WHERE m.code = ?
                              AND p.quantity > 0
                        """,
                        String.class,
                        marketCode));
    }

    private void batchUpdateSnapshots(List<Snapshot> snapshots) {
        var args =
                snapshots.stream()
                        .map(
                                s -> {
                                    var change = s.last().subtract(s.previousClose());
                                    var percent =
                                            Calculator.divide(
                                                    change.multiply(BigDecimal.valueOf(100)),
                                                    s.previousClose());

                                    return new Object[] {
                                        s.instrumentId(),
                                        s.currencyCode(),
                                        s.last(),
                                        s.previousClose(),
                                        change,
                                        percent,
                                        s.updatedAt()
                                    };
                                })
                        .toList();

        jdbcTemplate.batchUpdate(
                """
                    INSERT INTO instrument.instrument_snapshots (
                        instrument_id,
                        currency_code,
                        last,
                        previous_close,
                        daily_change,
                        daily_change_percent,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (instrument_id, currency_code)
                    DO UPDATE SET
                        last = EXCLUDED.last,
                        previous_close = EXCLUDED.previous_close,
                        daily_change = EXCLUDED.daily_change,
                        daily_change_percent = EXCLUDED.daily_change_percent,
                        updated_at = EXCLUDED.updated_at
                    WHERE EXCLUDED.updated_at > instrument.instrument_snapshots.updated_at
                       OR (
                           EXCLUDED.updated_at = instrument.instrument_snapshots.updated_at
                           AND (
                               instrument.instrument_snapshots.last IS DISTINCT FROM EXCLUDED.last
                               OR instrument.instrument_snapshots.previous_close
                                   IS DISTINCT FROM EXCLUDED.previous_close
                           )
                       )
                """,
                args);
    }
}
