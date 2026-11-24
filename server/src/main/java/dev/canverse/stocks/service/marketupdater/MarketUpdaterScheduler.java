package dev.canverse.stocks.service.marketupdater;

import dev.canverse.stocks.config.MarketUpdaterProperties;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.TimeZone;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketUpdaterScheduler {

    private final TaskScheduler taskScheduler;
    private final MarketUpdaterProperties marketUpdaterProperties;
    private final MarketPriceSyncService priceSyncService;
    private final MarketInstrumentSyncService instrumentSyncService;

    @PostConstruct
    public void scheduleAll() {
        marketUpdaterProperties
                .getMarkets()
                .forEach(
                        (marketKey, config) -> {
                            var marketCode = marketKey.toUpperCase(Locale.ROOT);
                            if (!config.isEnabled()) {
                                log.atInfo()
                                        .addKeyValue("market", marketCode)
                                        .addKeyValue("enabled", false)
                                        .log("Market updater disabled");
                                return;
                            }

                            int minQuotes = Math.max(1, config.getMinQuotes());

                            if (config.getCron() != null && !config.getCron().isBlank()) {
                                taskScheduler.schedule(
                                        () -> priceSyncService.sync(marketCode, minQuotes),
                                        getCron(config.getCron(), config.getTimezone()));
                                log.atInfo()
                                        .addKeyValue("job", "market_price_sync")
                                        .addKeyValue("market", marketCode)
                                        .addKeyValue("cron", config.getCron())
                                        .addKeyValue("timezone", config.getTimezone())
                                        .log("Scheduled market price sync");
                            }

                            if (config.getDiscoveryCron() != null
                                    && !config.getDiscoveryCron().isBlank()) {
                                taskScheduler.schedule(
                                        () -> instrumentSyncService.sync(marketCode, minQuotes),
                                        getCron(config.getDiscoveryCron(), config.getTimezone()));
                                log.atInfo()
                                        .addKeyValue("job", "market_instrument_sync")
                                        .addKeyValue("market", marketCode)
                                        .addKeyValue("cron", config.getDiscoveryCron())
                                        .addKeyValue("timezone", config.getTimezone())
                                        .log("Scheduled market instrument discovery");
                            }
                        });
    }

    private CronTrigger getCron(String cron, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return new CronTrigger(cron);
        }

        return new CronTrigger(cron, TimeZone.getTimeZone(timezone));
    }
}
