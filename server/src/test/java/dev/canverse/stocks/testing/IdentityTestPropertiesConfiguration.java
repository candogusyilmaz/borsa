package dev.canverse.stocks.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;

@TestConfiguration(proxyBeanMethods = false)
@PropertySource("classpath:identity-test.properties")
public class IdentityTestPropertiesConfiguration {
}
