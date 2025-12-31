package dev.canverse.stocks.service.marketupdater;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.canverse.stocks.domain.Calculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class MarketUpdater {
    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, Map<String, Long>> instrumentCache;

    protected MarketUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.instrumentCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .build();
    }

    public abstract String getMarketCode();

    protected abstract List<Snapshot> fetchSnapshots(Map<String, Long> instruments);

    @Transactional
    public void update() {
        var instruments = instrumentCache.get(getMarketCode(), this::fetchInstruments);
        long startFetch = System.currentTimeMillis();
        var snapshots = fetchSnapshots(instruments);
        log.info("[MarketUpdater] Fetched {} snapshots for {} in {} ms", snapshots.size(), getMarketCode(), System.currentTimeMillis() - startFetch);

        if (snapshots.isEmpty()) {
            log.warn("No snapshots generated for {}", getMarketCode());
            return;
        }

        long startBatch = System.currentTimeMillis();
        batchUpdateSnapshots(snapshots);
        log.info("[MarketUpdater] {} stock snapshots updated in {} ms", getMarketCode(), System.currentTimeMillis() - startBatch);
    }

    private Map<String, Long> fetchInstruments(String marketCode) {
        return jdbcTemplate.query("""
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
                }
        );
    }

    private void batchUpdateSnapshots(List<Snapshot> snapshots) {
        var args = snapshots.stream()
                .map(s -> {
                    var change = s.last().subtract(s.previousClose());
                    var percent = Calculator.divide(
                            change.multiply(BigDecimal.valueOf(100)),
                            s.previousClose()
                    );

                    return new Object[]{
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

        jdbcTemplate.batchUpdate("""
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
                """, args);
    }
}
