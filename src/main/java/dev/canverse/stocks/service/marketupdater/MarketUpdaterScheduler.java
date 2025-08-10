package dev.canverse.stocks.service.marketupdater;

import dev.canverse.stocks.config.MarketUpdaterProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.TimeZone;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketUpdaterScheduler {
    private final TaskScheduler taskScheduler;
    private final List<MarketUpdater> updaters;
    private final MarketUpdaterProperties marketUpdaterProperties;

    @PostConstruct
    public void scheduleAll() {
        for (MarketUpdater updater : updaters) {
            var marketConfig = marketUpdaterProperties.getMarkets().get(updater.getMarketCode().toLowerCase());

            if (marketConfig == null) {
                log.warn("No configuration found for market: {}", updater.getMarketCode());
                continue;
            }

            if (!marketConfig.isEnabled()) {
                log.info("Market updater for {} is disabled, skipping scheduling", updater.getMarketCode());
                continue;
            }

            taskScheduler.schedule(updater::update, getCron(marketConfig.getCron(), marketConfig.getTimezone()));
            log.info("Scheduled market updater for {} with cron: {} and timezone: {}",
                    updater.getMarketCode(), marketConfig.getCron(), marketConfig.getTimezone());
        }
    }

    private CronTrigger getCron(String cron, String timezone) {
        return new CronTrigger(cron, timezone != null ? TimeZone.getTimeZone(timezone) : TimeZone.getTimeZone("UTC"));
    }
}
