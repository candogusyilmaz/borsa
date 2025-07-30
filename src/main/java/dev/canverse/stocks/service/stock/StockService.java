package dev.canverse.stocks.service.stock;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.domain.entity.QStock;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.repository.StockSplitRepository;
import dev.canverse.stocks.service.client.SabahClient;
import dev.canverse.stocks.service.client.model.CanliBorsaVerileri;
import dev.canverse.stocks.service.stock.model.Stocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final JPAQueryFactory queryFactory;
    private final SabahClient sabahClient;
    private final JdbcTemplate jdbcTemplate;
    private final StockSplitRepository stockSplitRepository;
    private final HoldingRepository holdingRepository;

    public Stocks fetchStocks(String exchange) {
        var stock = QStock.stock;

        var result = queryFactory
                .select(Projections.constructor(Stocks.Symbol.class,
                        stock.id.stringValue(),
                        stock.symbol,
                        stock.name,
                        stock.snapshot.last,
                        stock.snapshot.dailyChange,
                        stock.snapshot.dailyChangePercent,
                        stock.snapshot.updatedAt))
                .from(stock)
                .where(stock.exchange.code.eq(exchange))
                .orderBy(stock.symbol.asc())
                .fetch();

        return new Stocks(exchange, result);
    }

    public List<SelectItem> fetchLookupStocks(Optional<String> exchange) {
        var stock = QStock.stock;

        var query = queryFactory
                .select(Projections.constructor(SelectItem.class, stock.id.stringValue(), stock.symbol.concat(" - ").concat(stock.name)))
                .from(stock);

        exchange.ifPresent(s -> query.where(stock.exchange.code.eq(s)));

        return query.fetch();
    }

    @Async
    @Transactional(timeout = 25)
    public void updateBist() {
        updateBistSync();
    }

    public void updateBistSync() {
        log.info("Updating stock snapshots from BIST");
        var resp = sabahClient.fetchBistStocks();

        if (resp.data().isEmpty())
            return;

        var stockIdMap = fetchStockIdMap();

        var batchArgs = prepareBatchArgs(resp, stockIdMap);

        if (!batchArgs.isEmpty()) {
            log.info("Updating stock snapshots for {} stocks", batchArgs.size());
            jdbcTemplate.execute("TRUNCATE TABLE stock_snapshots");
            batchInsertStockSnapshots(batchArgs);
            log.info("Stock snapshots updated successfully");
        }
    }

    private Map<String, Integer> fetchStockIdMap() {
        return jdbcTemplate.query(
                "SELECT symbol, id FROM stocks",
                rs -> {
                    Map<String, Integer> map = new HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getString("symbol"), rs.getInt("id"));
                    }
                    return map;
                }
        );
    }

    private List<Object[]> prepareBatchArgs(CanliBorsaVerileri resp, Map<String, Integer> stockIdMap) {
        return resp.data().stream()
                .map(s -> {
                    Integer stockId = stockIdMap.get(s.symbol());
                    if (stockId == null) {
                        return null;
                    }
                    return new Object[]{
                            stockId,
                            s.price(),
                            s.time().atOffset(ZoneOffset.UTC), // created_at
                            s.time().atOffset(ZoneOffset.UTC), // updated_at
                            s.change(),
                            s.changePercent(),
                            s.price().subtract(s.change())
                    };
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void batchInsertStockSnapshots(List<Object[]> batchArgs) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO stock_snapshots (stock_id, last, created_at, updated_at, daily_change, daily_change_percent, previous_close) VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchArgs,
                new int[]{
                        Types.INTEGER,
                        Types.NUMERIC,
                        Types.TIMESTAMP_WITH_TIMEZONE,
                        Types.TIMESTAMP_WITH_TIMEZONE,
                        Types.NUMERIC,
                        Types.NUMERIC,
                        Types.NUMERIC
                }
        );
    }

    @Transactional
    public void processStockSplits() {
        log.info("Processing stock splits");
        var today = LocalDate.now();
        var pendingSplits = stockSplitRepository.findPendingSplits(today);

        for (var split : pendingSplits) {
            log.info("Processing stock split for {}: ratio {}", split.getStock().getSymbol(), split.getRatio());

            var affectedHoldings = holdingRepository.findByStockId(split.getStock().getId());

            for (var holding : affectedHoldings) {
                holding.adjustStockSplit(split.getRatio());

                holdingRepository.save(holding);
                log.info("Updated holding for portfolio {} - Stock: {}", holding.getPortfolio().getId(), split.getStock().getSymbol());
            }

            split.setProcessed(true);
        }

        stockSplitRepository.saveAll(pendingSplits);
        log.info("Finished processing stock splits");
    }
}
