package dev.canverse.stocks.service.marketupdater;

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
        var code = marketCode.toUpperCase(Locale.ROOT);
        var instruments = instrumentCache.get(code, this::fetchInstruments);

        if (instruments == null || instruments.isEmpty()) {
            log.warn("[{}] No instruments found in DB, skipping price sync", code);
            return;
        }

        long startFetch = System.currentTimeMillis();
        var heldSymbols = fetchHeldSymbols(code);
        var quotes = quoteFetcher.fetchForPricing(code, minQuotes, heldSymbols);
        if (quotes.isEmpty()) {
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

        log.info(
                "[{}] Matched {} snapshots in {} ms",
                code,
                snapshots.size(),
                System.currentTimeMillis() - startFetch);

        if (snapshots.isEmpty()) {
            return;
        }

        long startBatch = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> batchUpdateSnapshots(snapshots));
        log.info(
                "[{}] Updated {} snapshots in {} ms",
                code,
                snapshots.size(),
                System.currentTimeMillis() - startBatch);
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
