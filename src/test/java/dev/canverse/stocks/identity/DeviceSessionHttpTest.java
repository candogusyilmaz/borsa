package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
@Import(DeviceSessionHttpTest.TestOverrides.class)
class DeviceSessionHttpTest {

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
    void listsOwnerSessionsAndRetrievesDetail() throws Exception {
        var email = "sessiontest@example.com";
        var userId = registrationService.register(email, "correct horse battery staple");
        var session1 = sessionIssuanceService.issue(userId, "laptop");
        var session2 = sessionIssuanceService.issue(userId, "phone");
        var accessToken = tokenIssuanceService.issue(session1.sessionId()).accessToken();
        var expectedFamilyIds = List.of(session1.sessionId(), session2.sessionId()).stream().sorted(Comparator.comparing(UUID::toString).reversed()).toList();
        var legacyCursorField = "next" + "Cursor";

        mockMvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$.sessions").doesNotExist()).andExpect(jsonPath("$[0].familyId", equalTo(expectedFamilyIds.get(0).toString())))
                .andExpect(jsonPath("$[1].familyId", equalTo(expectedFamilyIds.get(1).toString())))
                .andExpect(jsonPath("$[0].createdAt", equalTo(T0.toString()))).andExpect(jsonPath("$[1].createdAt", equalTo(T0.toString())))
                .andExpect(jsonPath("$[0].status", equalTo("ACTIVE"))).andExpect(jsonPath("$[1].status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$[0].current", equalTo(expectedFamilyIds.get(0).equals(session1.sessionId()))))
                .andExpect(jsonPath("$[1].current", equalTo(expectedFamilyIds.get(1).equals(session1.sessionId()))))
                .andExpect(jsonPath("$[0]." + legacyCursorField).doesNotExist()).andExpect(jsonPath("$[0].page").doesNotExist())
                .andExpect(jsonPath("$[0].size").doesNotExist()).andExpect(jsonPath("$[0].hasNext").doesNotExist())
                .andExpect(jsonPath("$." + legacyCursorField).doesNotExist()).andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist()).andExpect(jsonPath("$.hasNext").doesNotExist())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        mockMvc.perform(get("/api/v1/auth/sessions/" + session1.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.familyId", equalTo(session1.sessionId().toString()))).andExpect(jsonPath("$.current", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE"))).andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void crossOwnerSessionDetailReturnsNotFound() throws Exception {
        var user1 = registrationService.register("user1@example.com", "correct horse battery staple");
        var user2 = registrationService.register("user2@example.com", "correct horse battery staple");

        var s1 = sessionIssuanceService.issue(user1, "u1-dev");
        var s2 = sessionIssuanceService.issue(user2, "u2-dev");

        var token1 = tokenIssuanceService.issue(s1.sessionId()).accessToken();

        mockMvc.perform(get("/api/v1/auth/sessions/" + s2.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("SESSION_NOT_FOUND")));
    }

    @Test
    void deleteCurrentFamilyEmitsExpiredCookie() throws Exception {
        var userId = registrationService.register("delcurrent@example.com", "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "laptop");
        var accessToken = tokenIssuanceService.issue(session.sessionId()).accessToken();

        mockMvc.perform(delete("/api/v1/auth/sessions/" + session.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE)).andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void deleteOtherFamilyDoesNotEmitCookie() throws Exception {
        var userId = registrationService.register("delother@example.com", "correct horse battery staple");
        var session1 = sessionIssuanceService.issue(userId, "laptop");
        var session2 = sessionIssuanceService.issue(userId, "phone");
        var accessToken = tokenIssuanceService.issue(session1.sessionId()).accessToken();

        mockMvc.perform(delete("/api/v1/auth/sessions/" + session2.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void deleteCrossOwnerSessionReturnsNotFound() throws Exception {
        var user1 = registrationService.register("u1@example.com", "correct horse battery staple");
        var user2 = registrationService.register("u2@example.com", "correct horse battery staple");

        var s1 = sessionIssuanceService.issue(user1, "u1-dev");
        var s2 = sessionIssuanceService.issue(user2, "u2-dev");

        var token1 = tokenIssuanceService.issue(s1.sessionId()).accessToken();

        mockMvc.perform(delete("/api/v1/auth/sessions/" + s2.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("SESSION_NOT_FOUND")));
    }

    @Test
    void deleteEndedFamilyIsIdempotentNoContent() throws Exception {
        var userId = registrationService.register("idempotent-del@example.com", "correct horse battery staple");
        var session1 = sessionIssuanceService.issue(userId, "laptop");
        var session2 = sessionIssuanceService.issue(userId, "phone");
        var token1 = tokenIssuanceService.issue(session1.sessionId()).accessToken();

        // First deletion
        mockMvc.perform(delete("/api/v1/auth/sessions/" + session2.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNoContent());

        // Repeated deletion of already ended owned family is idempotent 204
        mockMvc.perform(delete("/api/v1/auth/sessions/" + session2.sessionId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNoContent());
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
