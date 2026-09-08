package dev.canverse.stocks.identity.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stocks.identity.refresh-session")
public record RefreshSessionProperties(@DefaultValue("30d") Duration lifetime) {

    public RefreshSessionProperties {
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
    }
}
