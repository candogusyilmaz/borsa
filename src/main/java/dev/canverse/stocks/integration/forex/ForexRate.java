package dev.canverse.stocks.integration.forex;

import java.math.BigDecimal;
import java.util.Map;

public record ForexRate(boolean success, Error error, String base, Long timestamp, Map<String, BigDecimal> rates) {
    public record Error(Integer code, String info) {
    }
}
