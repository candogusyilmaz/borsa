package dev.canverse.stocks.service.common;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;

import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.integration.forex.ForexWebClient;
import dev.canverse.stocks.repository.CurrencyRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {
    private static final String JOB_NAME = "currency_rate_sync";
    private static final String PROVIDER_NAME = "forex_rate_api";

    private final ForexWebClient forexWebClient;
    private final CurrencyRepository currencyRepository;

    public List<SelectItem> getAllCurrencies() {
        return currencyRepository.findAll()
            .stream()
            .map(c -> new SelectItem(c.getId().toString(), c.getCode() + " - " + c.getName()))
            .toList();
    }

    @Scheduled(cron = "0 0 20 * * ?")
    protected void updateCurrencyRates() {
        long startNanos = System.nanoTime();
        try {
            var result = forexWebClient.getForexRates();

            if (result == null || !result.success()) {
                var errorBuilder = log.atError()
                    .addKeyValue("event", "scheduled_job_failed")
                    .addKeyValue("job", JOB_NAME)
                    .addKeyValue("provider", PROVIDER_NAME)
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("durationMs", elapsedMs(startNanos));
                if (result != null && result.error() != null && result.error().code() != null) {
                    errorBuilder.addKeyValue("errorCode", result.error().code());
                }
                errorBuilder.log("Currency rate sync failed");
                return;
            }

            var currencies = currencyRepository.findAll();
            int updatedCount = 0;
            int missingCount = 0;

            for (var currency : currencies) {
                var rate = result.rates() != null ? result.rates().get(currency.getCode()) : null;

                if (rate == null) {
                    missingCount++;
                    log.atDebug()
                        .addKeyValue("job", JOB_NAME)
                        .addKeyValue("provider", PROVIDER_NAME)
                        .addKeyValue("currencyCode", currency.getCode())
                        .log("No exchange rate returned for currency");
                    continue;
                }

                currency.updateExchangeRate(rate, Instant.ofEpochSecond(result.timestamp()));
                updatedCount++;
            }

            currencyRepository.saveAll(currencies);

            var outcome = missingCount == 0 ? "success" : "partial";
            var completionLog = missingCount == 0 ? log.atInfo() : log.atWarn();
            completionLog.addKeyValue("event", "scheduled_job_completed")
                .addKeyValue("job", JOB_NAME)
                .addKeyValue("provider", PROVIDER_NAME)
                .addKeyValue("processedCount", currencies.size())
                .addKeyValue("succeededCount", updatedCount)
                .addKeyValue("failedCount", missingCount)
                .addKeyValue("durationMs", elapsedMs(startNanos))
                .addKeyValue("outcome", outcome)
                .log("Currency rate sync completed");
        } catch (Exception ex) {
            log.atError()
                .addKeyValue("event", "scheduled_job_failed")
                .addKeyValue("job", JOB_NAME)
                .addKeyValue("provider", PROVIDER_NAME)
                .addKeyValue("outcome", "failed")
                .addKeyValue("durationMs", elapsedMs(startNanos))
                .setCause(ex)
                .log("Currency rate sync failed unexpectedly");
            throw ex;
        }
    }
}
