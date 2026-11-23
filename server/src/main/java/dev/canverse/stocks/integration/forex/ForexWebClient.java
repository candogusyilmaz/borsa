package dev.canverse.stocks.integration.forex;

import org.springframework.web.service.annotation.GetExchange;

public interface ForexWebClient {
    @GetExchange("/v1/latest?base=USD")
    ForexRate getForexRates();
}
