package dev.canverse.stocks.testing;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

public final class SuitePostgresContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        var postgres = SuitePostgres.start();
        applicationContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource("suitePostgres", Map.of("spring.datasource.url",
                postgres.getJdbcUrl(), "spring.datasource.username", postgres.getUsername(), "spring.datasource.password", postgres.getPassword())));
    }
}
