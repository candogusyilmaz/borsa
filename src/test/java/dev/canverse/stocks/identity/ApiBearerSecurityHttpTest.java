package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "stocks.identity.refresh-session.lifetime=2h",
            "stocks.identity.access-token.issuer=https://issuer.test",
            "stocks.identity.access-token.audience=canverse-test-api",
            "stocks.identity.access-token.lifetime=5m",
            "stocks.identity.access-token.key-id=test-ephemeral"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import({ApiBearerSecurityHttpTest.TestOverrides.class, ApiBearerSecurityHttpTest.ProbeController.class})
class ApiBearerSecurityHttpTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-09T17:00:00.750Z");
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String API_PROBE_PATH = "/api/v1/test/authentication-probe";
    private static final String OUTSIDE_PROBE_PATH = "/test/outside-security";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    LocalLoginService localLoginService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @Autowired
    ProbeController probeController;

    @BeforeEach
    void setUp() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.reset();
        probeController.reset();
    }

    @Test
    void chainHasExactScopeAndRegistrationAndLoginArePublic() throws Exception {
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        var registrationTraceId = uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
        var userAccountId = uuid("10000000-0000-4000-8000-000000000001");
        var authIdentityId = uuid("20000000-0000-4000-8000-000000000002");
        idGenerator.setNextIds(registrationTraceId, userAccountId, authIdentityId);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("public-registration@example.com")))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, registrationTraceId.toString()))
                .andExpect(jsonPath("$.userId").value(userAccountId.toString()));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity.user_account", Long.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity.auth_identity", Long.class))
                .isOne();
        var afterRegistration = snapshot();

        var outsideTraceId = uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
        idGenerator.setNextIds(outsideTraceId);
        mockMvc.perform(get(OUTSIDE_PROBE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().string("outside"))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, outsideTraceId.toString()));
        assertThat(probeController.outsideInvocations()).isOne();

        var rejectedTraceId = uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3");
        idGenerator.setNextIds(rejectedTraceId);
        var rejected = mockMvc.perform(get(API_PROBE_PATH)).andReturn();
        assertInvalidCredentials(rejected, rejectedTraceId, API_PROBE_PATH, List.of());
        assertThat(probeController.apiInvocations()).isZero();
        assertThat(snapshot()).isEqualTo(afterRegistration);
    }

    @Test
    void expectedBearerFailuresShareOneSafeTracedProblemWithoutWrites() throws Exception {
        var issued = registerAndLogin(
                uuid("30000000-0000-4000-8000-000000000003"),
                uuid("40000000-0000-4000-8000-000000000004"),
                uuid("50000000-0000-4000-8000-000000000005"),
                uuid("60000000-0000-4000-8000-000000000006"),
                "revoked-http@example.com");
        runInTransaction(() -> jdbcTemplate.update(
                "UPDATE identity.device_session SET revoked_at = ?, revoke_reason = ? WHERE id = ?",
                OBSERVED_AT.atOffset(ZoneOffset.UTC),
                "test fixture revocation",
                issued.sessionId()));
        var beforeAuthentication = snapshot();
        var sensitiveValues = List.of(
                issued.accessToken(),
                issued.userAccountId().toString(),
                issued.sessionId().toString(),
                "InvalidBearerTokenException",
                "JwtValidationException",
                "BadJwtException",
                "test fixture revocation",
                "revoked",
                "signature");

        var missingTraceId = uuid("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
        idGenerator.setNextIds(missingTraceId);
        assertInvalidCredentials(
                mockMvc.perform(get(API_PROBE_PATH)).andReturn(), missingTraceId, API_PROBE_PATH, sensitiveValues);
        assertThat(snapshot()).isEqualTo(beforeAuthentication);

        var malformedTraceId = uuid("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
        var malformedToken = "not-a-jwt";
        idGenerator.setNextIds(malformedTraceId);
        assertInvalidCredentials(
                mockMvc.perform(get(API_PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + malformedToken))
                        .andReturn(),
                malformedTraceId,
                API_PROBE_PATH,
                concat(sensitiveValues, malformedToken));
        assertThat(snapshot()).isEqualTo(beforeAuthentication);

        var revokedTraceId = uuid("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3");
        idGenerator.setNextIds(revokedTraceId);
        assertInvalidCredentials(
                mockMvc.perform(get(API_PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.accessToken()))
                        .andReturn(),
                revokedTraceId,
                API_PROBE_PATH,
                sensitiveValues);
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
        assertThat(probeController.apiInvocations()).isZero();
    }

    @Test
    void validCurrentBearerReachesMvcAsAuthorityFreeIdentityWithoutSessionOrWrites() throws Exception {
        var issued = registerAndLogin(
                uuid("70000000-0000-4000-8000-000000000007"),
                uuid("80000000-0000-4000-8000-000000000008"),
                uuid("90000000-0000-4000-8000-000000000009"),
                uuid("a0000000-0000-4000-8000-00000000000a"),
                "valid-http@example.com");
        var beforeAuthentication = snapshot();
        var traceId = uuid("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
        idGenerator.setNextIds(traceId);

        var result = mockMvc.perform(
                        get(API_PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andExpect(jsonPath("$.name").value(issued.userAccountId().toString()))
                .andExpect(jsonPath("$.sid").value(issued.sessionId().toString()))
                .andExpect(jsonPath("$.authorities").isEmpty())
                .andReturn();

        var authentication = probeController.lastAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(issued.userAccountId().toString());
        assertThat(authentication.getToken().getClaimAsString("sid"))
                .isEqualTo(issued.sessionId().toString());
        assertThat(authentication.getAuthorities()).isEmpty();
        assertNoSession(result);
        assertThat(probeController.apiInvocations()).isOne();
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    @Test
    void successfulBearerAuthenticationIsNotPersistedForAHeaderlessRequest() throws Exception {
        var issued = registerAndLogin(
                uuid("b0000000-0000-4000-8000-00000000000b"),
                uuid("c0000000-0000-4000-8000-00000000000c"),
                uuid("d0000000-0000-4000-8000-00000000000d"),
                uuid("e0000000-0000-4000-8000-00000000000e"),
                "stateless-http@example.com");
        var beforeAuthentication = snapshot();
        var authenticatedTraceId = uuid("dddddddd-dddd-4ddd-8ddd-ddddddddddd1");
        idGenerator.setNextIds(authenticatedTraceId);
        var authenticated = mockMvc.perform(
                        get(API_PROBE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, authenticatedTraceId.toString()))
                .andReturn();
        assertNoSession(authenticated);

        var headerlessTraceId = uuid("dddddddd-dddd-4ddd-8ddd-ddddddddddd2");
        idGenerator.setNextIds(headerlessTraceId);
        var headerless = mockMvc.perform(get(API_PROBE_PATH)).andReturn();
        assertInvalidCredentials(headerless, headerlessTraceId, API_PROBE_PATH, List.of());

        assertThat(probeController.apiInvocations()).isOne();
        assertThat(snapshot()).isEqualTo(beforeAuthentication);
    }

    private IssuedIdentity registerAndLogin(
            UUID userAccountId, UUID authIdentityId, UUID sessionId, UUID tokenId, String email) {
        idGenerator.setNextIds(userAccountId, authIdentityId, sessionId, tokenId);
        assertThat(registrationService.register(email, RAW_PASSWORD)).isEqualTo(userAccountId);
        var login = localLoginService.login(email, RAW_PASSWORD, "test device");
        assertThat(login.sessionId()).isEqualTo(sessionId);
        return new IssuedIdentity(userAccountId, sessionId, login.accessToken());
    }

    private void assertInvalidCredentials(
            MvcResult result, UUID expectedTraceId, String expectedPath, List<String> sensitiveValues)
            throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeaders(HttpHeaders.WWW_AUTHENTICATE)).containsExactly("Bearer");
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isEqualTo(expectedTraceId.toString());

        var body = response.getContentAsString();
        var problem = JsonPath.<Map<String, Object>>read(body, "$");
        assertThat(problem)
                .containsOnlyKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp")
                .containsEntry("type", "https://canverse.dev/problems/invalid-credentials")
                .containsEntry("title", "Unauthorized")
                .containsEntry("status", 401)
                .containsEntry("instance", expectedPath)
                .containsEntry("code", "INVALID_CREDENTIALS")
                .containsEntry("key", "error.identity.invalid_credentials")
                .containsEntry("traceId", expectedTraceId.toString());
        assertThat(Instant.parse(problem.get("timestamp").toString())).isEqualTo(OBSERVED_AT);
        if (!sensitiveValues.isEmpty()) {
            assertThat(body).doesNotContain(sensitiveValues);
        }
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private void assertNoSession(MvcResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("JSESSIONID="));
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private PersistedIdentityState snapshot() {
        return new PersistedIdentityState(
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.device_session ORDER BY id")));
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private String registrationJson(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, RAW_PASSWORD);
    }

    private List<String> concat(List<String> values, String extra) {
        var combined = new ArrayList<>(values);
        combined.add(extra);
        return List.copyOf(combined);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }
    }

    @RestController
    static class ProbeController {

        private final AtomicInteger apiInvocations = new AtomicInteger();
        private final AtomicInteger outsideInvocations = new AtomicInteger();
        private final AtomicReference<JwtAuthenticationToken> lastAuthentication = new AtomicReference<>();

        @GetMapping(API_PROBE_PATH)
        Map<String, Object> apiProbe(Authentication authentication) {
            apiInvocations.incrementAndGet();
            var jwtAuthentication = (JwtAuthenticationToken) authentication;
            lastAuthentication.set(jwtAuthentication);
            return Map.of(
                    "name", jwtAuthentication.getName(),
                    "sid", jwtAuthentication.getToken().getClaimAsString("sid"),
                    "authorities", jwtAuthentication.getAuthorities());
        }

        @GetMapping(OUTSIDE_PROBE_PATH)
        String outsideProbe() {
            outsideInvocations.incrementAndGet();
            return "outside";
        }

        void reset() {
            apiInvocations.set(0);
            outsideInvocations.set(0);
            lastAuthentication.set(null);
        }

        int apiInvocations() {
            return apiInvocations.get();
        }

        int outsideInvocations() {
            return outsideInvocations.get();
        }

        JwtAuthenticationToken lastAuthentication() {
            return lastAuthentication.get();
        }
    }

    static final class RecordingIdGenerator implements IdGenerator {

        private final Deque<UUID> nextIds = new ArrayDeque<>();

        void setNextIds(UUID... ids) {
            nextIds.clear();
            nextIds.addAll(Arrays.asList(ids));
        }

        void reset() {
            setNextIds();
        }

        @Override
        public UUID next() {
            return nextIds.isEmpty() ? UUID.randomUUID() : nextIds.removeFirst();
        }
    }

    private record IssuedIdentity(UUID userAccountId, UUID sessionId, String accessToken) {}

    private record PersistedIdentityState(
            List<Map<String, Object>> users,
            List<Map<String, Object>> identities,
            List<Map<String, Object>> sessions) {}
}
