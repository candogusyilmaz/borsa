package dev.canverse.stocks.testing;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new DatabaseCleaner(jdbcTemplate, transactionManager);
    }

    @Bean
    TestIdentitySupport testIdentitySupport(UserAccountRepository userAccountRepository, RefreshSessionIssuanceService refreshSessionIssuanceService,
            AccessTokenIssuanceService accessTokenIssuanceService, Clock clock) {
        return new TestIdentitySupport(userAccountRepository, refreshSessionIssuanceService, accessTokenIssuanceService, clock);
    }
}
