package dev.canverse.stocks.service.common;

import dev.canverse.stocks.integration.forex.ForexWebClient;
import dev.canverse.stocks.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {
    private final ForexWebClient forexWebClient;
    private final CurrencyRepository currencyRepository;

    @Scheduled(cron = "0 0 20 * * ?")
    protected void updateCurrencyRates() {
        var result = forexWebClient.getForexRates();

        if (!result.success()) {
            log.error("Failed to fetch forex rates: {}", result.error().info());
            return;
        }

        var currencies = currencyRepository.findAll();

        for (var currency : currencies) {
            var rate = result.rates().get(currency.getCode());

            if (rate == null) {
                log.warn("No rate found for currency {}", currency.getCode());
                continue;
            }

            currency.updateExchangeRate(rate, Instant.ofEpochSecond(result.timestamp()));
            log.info("Updated currency {} with rate {}", currency.getCode(), rate);
        }

        currencyRepository.saveAll(currencies);
        log.info("Currency rates updated successfully");
    }
}
