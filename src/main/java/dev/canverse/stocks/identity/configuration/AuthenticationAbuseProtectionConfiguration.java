package dev.canverse.stocks.identity.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationAbuseProtectionProperties.class)
public class AuthenticationAbuseProtectionConfiguration {
}
