package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"stocks.identity.refresh-session.lifetime=30d"})
@AutoConfigureMockMvc
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class LocalLogoutHttpTest {

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
    void currentSessionLogoutReturnsNoContentAndClearsCookie() throws Exception {
        var userId = registrationService.register("currentlogout@example.com", "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "laptop");
        var accessToken = tokenIssuanceService.issue(session.sessionId()).accessToken();

        mockMvc.perform(
                post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "scope": "CURRENT_SESSION"
                        }
                        """)).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache")).andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("1970")))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        // Calling /me with same token now fails
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void allSessionsLogoutRevokesEveryDevice() throws Exception {
        var userId = registrationService.register("alllogout@example.com", "correct horse battery staple");
        var s1 = sessionIssuanceService.issue(userId, "laptop");
        var s2 = sessionIssuanceService.issue(userId, "phone");
        var token1 = tokenIssuanceService.issue(s1.sessionId()).accessToken();
        var token2 = tokenIssuanceService.issue(s2.sessionId()).accessToken();

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token1).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "scope": "ALL_SESSIONS"
                }
                """)).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        // Both tokens now fail
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token2)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidScopeReturnsValidationOrMalformedError() throws Exception {
        var userId = registrationService.register("badscope@example.com", "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "laptop");
        var accessToken = tokenIssuanceService.issue(session.sessionId()).accessToken();

        mockMvc.perform(
                post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "scope": "UNKNOWN_SCOPE"
                        }
                        """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", equalTo("MALFORMED_REQUEST")));
    }

}
