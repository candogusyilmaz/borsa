package dev.canverse.stocks.service.marketupdater.provider;

import dev.canverse.stocks.service.marketupdater.MarketQuote;
import dev.canverse.stocks.service.marketupdater.MarketQuoteProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Order(1)
@ConditionalOnProperty(
        name = "market-updater.providers.tradingview.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TradingViewBistQuoteProvider implements MarketQuoteProvider {

    private final RestClient restClient;

    public TradingViewBistQuoteProvider(RestClient.Builder builder) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient =
                builder.baseUrl("https://scanner.tradingview.com")
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                        .build();
    }

    @Override
    public String getMarketCode() {
        return "BIST";
    }

    @Override
    public String getName() {
        return "TradingView";
    }

    @Override
    public boolean supportsDiscovery() {
        return true;
    }

    @Override
    public List<MarketQuote> fetchQuotes() {
        var request =
                Map.of(
                        "filter",
                        List.of(
                                Map.of(
                                        "left",
                                        "type",
                                        "operation",
                                        "in_range",
                                        "right",
                                        List.of("stock", "fund"))),
                        "options",
                        Map.of("lang", "tr"),
                        "columns",
                        List.of("name", "description", "close", "change_abs", "time|1", "time"),
                        "sort",
                        Map.of("sortBy", "name", "sortOrder", "asc"),
                        "range",
                        List.of(0, 1000));

        var response =
                restClient
                        .post()
                        .uri("/turkey/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(TvScanResponse.class);

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .map(row -> toQuote(row.d()))
                .filter(Objects::nonNull)
                .toList();
    }

    private MarketQuote toQuote(List<Object> cols) {
        if (cols == null || cols.size() < 6 || cols.get(0) == null || cols.get(2) == null) {
            return null;
        }

        var symbol = cols.get(0).toString().trim();
        if (symbol.isEmpty()) {
            return null;
        }

        var rawName = cols.get(1) != null ? cols.get(1).toString().trim() : "";
        var name = rawName.isEmpty() ? symbol : rawName;

        var last = new BigDecimal(cols.get(2).toString());
        if (last.signum() <= 0) {
            return null;
        }

        var changeAbs =
                cols.get(3) != null ? new BigDecimal(cols.get(3).toString()) : BigDecimal.ZERO;
        var previousClose = last.subtract(changeAbs);
        if (previousClose.signum() <= 0) {
            return null;
        }

        var updatedAt = parseEpochSeconds(cols.get(4));
        if (updatedAt == null) {
            updatedAt = parseEpochSeconds(cols.get(5));
        }
        if (updatedAt == null) {
            return null;
        }

        return new MarketQuote(symbol, name, "TRY", last, previousClose, updatedAt);
    }

    private Instant parseEpochSeconds(Object value) {
        if (value instanceof Number number) {
            long epochSeconds = number.longValue();
            return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
        }
        return null;
    }

    private record TvScanResponse(int totalCount, List<TvRow> data) {}

    private record TvRow(String s, List<Object> d) {}
}
