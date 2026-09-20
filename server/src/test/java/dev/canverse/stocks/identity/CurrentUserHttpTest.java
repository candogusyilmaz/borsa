package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"stocks.identity.refresh-session.lifetime=30d"})
@AutoConfigureMockMvc
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class CurrentUserHttpTest {

    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    TestClock testClock;
    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");
    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService sessionIssuanceService;

    @Autowired
    AccessTokenIssuanceService tokenIssuanceService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        testClock.setInstant(T0);

        databaseCleaner.resetApplicationState();
    }

    @Test
    void returnsAuthenticatedCurrentUserData() throws Exception {
        var email = "me@example.com";
        var userId = registrationService.register(email, "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "desktop");
        var accessToken = tokenIssuanceService.issue(session.sessionId()).accessToken();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.id", equalTo(userId.toString()))).andExpect(jsonPath("$.email", equalTo(email)))
                .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.emailNormalized").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist()).andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
    }

    @Test
    void invalidBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
    }

}
