package dev.canverse.stocks.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.market-updaters")
public class MarketUpdaterProperties {
    private Map<String, MarketConfig> markets = new HashMap<>();

    @Getter
    @Setter
    public static class MarketConfig {
        private boolean enabled;
        private String cron;
        private String timezone;
    }
}
