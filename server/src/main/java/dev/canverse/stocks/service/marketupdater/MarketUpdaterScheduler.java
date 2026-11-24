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
                            if (!config.isEnabled()) {
                                log.info("[{}] Market updater is disabled, skipping", marketKey);
                                return;
                            }

                            var marketCode = marketKey.toUpperCase(Locale.ROOT);
                            int minQuotes = Math.max(1, config.getMinQuotes());

                            if (config.getCron() != null && !config.getCron().isBlank()) {
                                taskScheduler.schedule(
                                        () -> priceSyncService.sync(marketCode, minQuotes),
                                        getCron(config.getCron(), config.getTimezone()));
                                log.info(
                                        "[{}] Scheduled price sync with cron: {} (tz: {})",
                                        marketCode,
                                        config.getCron(),
                                        config.getTimezone());
                            }

                            if (config.getDiscoveryCron() != null
                                    && !config.getDiscoveryCron().isBlank()) {
                                taskScheduler.schedule(
                                        () -> instrumentSyncService.sync(marketCode, minQuotes),
                                        getCron(config.getDiscoveryCron(), config.getTimezone()));
                                log.info(
                                        "[{}] Scheduled instrument discovery with cron: {} (tz:"
                                                + " {})",
                                        marketCode,
                                        config.getDiscoveryCron(),
                                        config.getTimezone());
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
