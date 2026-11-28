package dev.canverse.stocks.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "market-updater")
public class MarketUpdaterProperties {
    private Map<String, MarketConfig> markets = new HashMap<>();

    @Data
    public static class MarketConfig {
        private boolean enabled;
        private String cron;
        private String discoveryCron;
        private String timezone;
        private int minQuotes = 1;
    }
}
