package dev.canverse.stocks.service.marketupdater;

import dev.canverse.stocks.service.client.SabahClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BistMarketUpdater extends MarketUpdater {
    private final SabahClient sabahClient;

    public BistMarketUpdater(JdbcTemplate jdbcTemplate, SabahClient sabahClient) {
        super(jdbcTemplate);
        this.sabahClient = sabahClient;
    }

    @Override
    public String getMarketCode() {
        return "BIST";
    }

    @Override
    protected List<Snapshot> fetchSnapshots(Map<String, Long> instruments) {
        var resp = sabahClient.fetchBistStocks().data();

        return resp.stream()
                .map(item -> {
                    var id = instruments.get(item.symbol());
                    if (id == null) return null;

                    return new Snapshot(
                            "TRY",
                            item.price(),
                            item.price().subtract(item.change()),
                            Timestamp.from(item.time()),
                            id
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
