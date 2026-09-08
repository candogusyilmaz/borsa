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
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=30d", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@AutoConfigureMockMvc
@Testcontainers
@Import(CurrentUserHttpTest.TestOverrides.class)
class CurrentUserHttpTest {

    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

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
        jdbcTemplate.execute("TRUNCATE TABLE platform.security_event, identity.device_session, identity.auth_identity, identity.user_account CASCADE");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }
    }
}
