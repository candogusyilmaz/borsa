package dev.canverse.stocks.identity.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stocks.identity.access-token")
public record AccessTokenProperties(
        @DefaultValue("https://canverse.dev") URI issuer,
        @DefaultValue("canverse-api") String audience,
        @DefaultValue("15m") Duration lifetime,
        @DefaultValue("local-ephemeral") String keyId) {

    public AccessTokenProperties {
        if (issuer == null || !issuer.isAbsolute()) {
            throw new IllegalArgumentException("issuer must be an absolute URI");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience must not be blank");
        }
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
    }
}
