package dev.canverse.stocks.identity.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stocks.web.cors")
public record ApiCorsProperties(List<String> allowedOrigins) {

    public ApiCorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("allowedOrigins must not be empty");
        }
        allowedOrigins = allowedOrigins.stream().map(String::trim).toList();
        if (allowedOrigins.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("allowedOrigins must not contain blank values");
        }
    }
}
