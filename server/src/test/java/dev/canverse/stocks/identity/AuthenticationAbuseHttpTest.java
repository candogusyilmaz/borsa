package dev.canverse.stocks.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=30d", "stocks.identity.abuse-protection.login.principal-max-failures=3",
                "stocks.identity.abuse-protection.registration.source-max-attempts=3", "stocks.identity.abuse-protection.refresh.source-max-failures=3"})
@AutoConfigureMockMvc
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class AuthenticationAbuseHttpTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");
    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestClock mutableClock;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.resetApplicationState();
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

}
