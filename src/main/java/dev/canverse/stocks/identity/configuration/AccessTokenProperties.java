package dev.canverse.stocks.identity.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stocks.identity.access-token")
public record AccessTokenProperties(@DefaultValue("https://canverse.dev") URI issuer, @DefaultValue("canverse-api") String audience,
        @DefaultValue("15m") Duration lifetime, @DefaultValue("local-ephemeral") String keyId, @DefaultValue(" ") String privateKeyPem,
        @DefaultValue(" ") String publicKeyPem) {

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
        privateKeyPem = normalizeKeyPem(privateKeyPem);
        publicKeyPem = normalizeKeyPem(publicKeyPem);
        if ((privateKeyPem == null) != (publicKeyPem == null)) {
            throw new IllegalArgumentException("privateKeyPem and publicKeyPem must be configured together");
        }
    }

    private static String normalizeKeyPem(String keyPem) {
        if (keyPem == null || keyPem.isBlank()) {
            return null;
        }
        return keyPem.trim();
    }

    @Override
    public String toString() {
        return "AccessTokenProperties[issuer=" + issuer + ", audience=" + audience + ", lifetime=" + lifetime + ", keyId=" + keyId +
                ", privateKeyPemConfigured=" + (privateKeyPem != null) + ", publicKeyPemConfigured=" + (publicKeyPem != null) + "]";
    }
}
