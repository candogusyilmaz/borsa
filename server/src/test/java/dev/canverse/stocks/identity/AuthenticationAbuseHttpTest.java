package dev.canverse.stocks.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=30d", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral", "stocks.identity.abuse-protection.login.principal-max-failures=3",
                "stocks.identity.abuse-protection.registration.source-max-attempts=3", "stocks.identity.abuse-protection.refresh.source-max-failures=3"})
@AutoConfigureMockMvc
@Testcontainers
@Import(AuthenticationAbuseHttpTest.TestOverrides.class)
class AuthenticationAbuseHttpTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock mutableClock;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
        mutableClock.set(T0);
    }

    @Test
    void loginThrottlesAfterConfiguredFailuresAndReturns429() throws Exception {
        var email = "throttled@example.com";
        registrationService.register(email, "correct password");

        var badLoginJson = """
                {
                  "email": "%s",
                  "password": "wrong password",
                  "deviceLabel": "desktop",
                  "refreshTokenDelivery": "RESPONSE_BODY"
                }
                """.formatted(email);

        // Failures 1, 2, 3 return 401
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLoginJson)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
        }

        // 4th attempt returns 429 AUTHENTICATION_THROTTLED
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLoginJson)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_THROTTLED")));

        // Correct password while throttled also returns 429
        var goodLoginJson = """
                {
                  "email": "%s",
                  "password": "correct password",
                  "deviceLabel": "desktop",
                  "refreshTokenDelivery": "RESPONSE_BODY"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(goodLoginJson)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_THROTTLED")));

        // Advance clock past block duration (15m default)
        mutableClock.advance(Duration.ofMinutes(15));

        // Now login with good password succeeds!
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(goodLoginJson)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    void registrationThrottlesAfterMaxAttempts() throws Exception {
        // Attempts 1, 2, 3 succeed
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "email": "reg%d@example.com",
                      "password": "correct horse battery staple"
                    }
                    """.formatted(i))).andExpect(status().isCreated());
        }

        // Attempt 4 is throttled -> 429
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "email": "reg4@example.com",
                  "password": "correct horse battery staple"
                }
                """)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_THROTTLED")));
    }

    @Test
    void refreshThrottlesAfterConfiguredFailures() throws Exception {
        var badRefreshJson = """
                {
                  "refreshToken": "invalid-token",
                  "refreshTokenDelivery": "RESPONSE_BODY"
                }
                """;

        // Failures 1, 2, 3 return 401
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(badRefreshJson)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
        }

        // 4th attempt returns 429
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(badRefreshJson)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_THROTTLED")));
    }

    static class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant start) {
            this.current = start;
        }

        void set(Instant next) {
            this.current = next;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(T0);
        }
    }
}
