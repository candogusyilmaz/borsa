package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import dev.canverse.stocks.testing.RecordingIdGenerator;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=2h", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@AutoConfigureMockMvc
@Testcontainers
@Import(LocalRefreshHttpTest.TestOverrides.class)
class LocalRefreshHttpTest {

    private static final Instant LOGIN_TIME = Instant.parse("2026-08-15T12:00:00.750Z");
    private static final Instant REFRESH_TIME = LOGIN_TIME.plusSeconds(60);
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    LocalAccessTokenAuthenticationConverter accessTokenConverter;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext applicationContext;

    @BeforeEach
    void clearIdentityTables() {
        clock.setInstant(LOGIN_TIME);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.reset();
    }

    @Test
    void responseBodyRefreshReturnsOnlyReplacementBodyAndNoCookie() throws Exception {
        var login = login("10000000-0000-4000-8000-000000000001", "body@example.com", "RESPONSE_BODY");
        var replacementId = uuid("40000000-0000-4000-8000-000000000004");
        var refreshTrace = uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
        idGenerator.setNextIds(refreshTrace, replacementId, uuid("50000000-0000-4000-8000-000000000005"));

        var result = mockMvc
                .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"RESPONSE_BODY\"}".formatted(login.refreshToken())))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, refreshTrace.toString()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();

        var response = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(response.keySet()).containsExactlyInAnyOrder("sessionId", "accessToken", "accessTokenExpiresAt", "refreshTokenExpiresAt", "serverTime",
                "refreshToken");
        assertThat(response.get("sessionId")).isEqualTo(replacementId.toString());
        assertThat((String) response.get("refreshToken")).isNotEqualTo(login.refreshToken()).isNotBlank();
        assertThat(response.get("refreshTokenExpiresAt")).isEqualTo(LOGIN_TIME.plus(Duration.ofHours(2)).toString());
        assertThat(response.get("serverTime")).isEqualTo(LOGIN_TIME.toString());
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(deviceSessionRepository.count()).isEqualTo(2);
    }

    @Test
    void cookieRefreshOmitsBodyCredentialAndDecreasesMaxAge() throws Exception {
        var login = login("20000000-0000-4000-8000-000000000002", "cookie@example.com", "HTTP_ONLY_COOKIE");
        var loginCookie = login.setCookie();
        clock.setInstant(REFRESH_TIME);
        var refreshTrace = uuid("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
        var replacementId = uuid("60000000-0000-4000-8000-000000000006");
        idGenerator.setNextIds(refreshTrace, replacementId, uuid("70000000-0000-4000-8000-000000000007"));

        var result = mockMvc
                .perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh-token", loginCookie.getValue())).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content("{\"refreshTokenDelivery\":\"HTTP_ONLY_COOKIE\"}"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, refreshTrace.toString())).andReturn();

        var body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet()).containsExactlyInAnyOrder("sessionId", "accessToken", "accessTokenExpiresAt",
                "refreshTokenExpiresAt", "serverTime");
        var replacementCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(replacementCookie).hasSize(1);
        assertThat(replacementCookie.getFirst()).contains("refresh-token=").contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Expires=" + DateTimeFormatter.RFC_1123_DATE_TIME
                        .format(LOGIN_TIME.plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)))
                .doesNotContain("Domain=");
        assertThat(cookieMaxAge(replacementCookie.getFirst())).isLessThan(cookieMaxAge(login.setCookieHeader()));
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(deviceSessionRepository.count()).isEqualTo(2);
    }

    @Test
    void ambiguousCredentialsFailBeforeRotationAndDoNotLeakToken() throws Exception {
        var login = login("30000000-0000-4000-8000-000000000003", "ambiguous@example.com", "RESPONSE_BODY");
        var before = persistedState();
        var trace = uuid("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
        idGenerator.setNextIds(trace);

        var result = mockMvc
                .perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh-token", login.refreshToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"RESPONSE_BODY\"}".formatted(login.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, trace.toString())).andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(login.refreshToken());
        assertThat(persistedState()).isEqualTo(before);
        assertThat(idGenerator.consumedIds()).startsWith(trace);
    }

    @Test
    void missingBlankWrongChannelAndDuplicateCredentialsFailBeforeRotation() throws Exception {
        var login = login("30100000-0000-4000-8000-000000000003", "credential-matrix@example.com", "RESPONSE_BODY");
        var before = persistedState();
        var requests = List.of(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshTokenDelivery\":\"RESPONSE_BODY\"}"),
                post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \",\"refreshTokenDelivery\":\"RESPONSE_BODY\"}"),
                post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"HTTP_ONLY_COOKIE\"}".formatted(login.refreshToken())),
                post("/api/v1/auth/refresh").cookie(new Cookie("refresh-token", login.refreshToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"HTTP_ONLY_COOKIE\"}".formatted(login.refreshToken())),
                post("/api/v1/auth/refresh").cookie(new Cookie("refresh-token", login.refreshToken()), new Cookie("refresh-token", login.refreshToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshTokenDelivery\":\"HTTP_ONLY_COOKIE\"}"));
        var traces = List.of(uuid("c1000000-0000-4000-8000-000000000001"), uuid("c1000000-0000-4000-8000-000000000002"),
                uuid("c1000000-0000-4000-8000-000000000003"), uuid("c1000000-0000-4000-8000-000000000004"), uuid("c1000000-0000-4000-8000-000000000005"));
        for (var index = 0; index < requests.size(); index++) {
            var trace = traces.get(index);
            idGenerator.setNextIds(trace);
            var result = mockMvc.perform(requests.get(index).header(RequestTraceFilter.TRACE_ID_HEADER, trace).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(login.refreshToken());
            assertThat(persistedState()).isEqualTo(before);
            assertThat(idGenerator.consumedIds()).startsWith(trace);
        }
    }

    @Test
    void missingOrUnknownDeliveryIsRejectedBeforeRotation() throws Exception {
        var login = login("30200000-0000-4000-8000-000000000003", "delivery-matrix@example.com", "RESPONSE_BODY");
        var before = persistedState();
        idGenerator.setNextIds(uuid("c2000000-0000-4000-8000-000000000001"));
        mockMvc.perform(
                post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"%s\"}".formatted(login.refreshToken())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(persistedState()).isEqualTo(before);

        idGenerator.setNextIds(uuid("c2000000-0000-4000-8000-000000000002"));
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"UNKNOWN\"}".formatted(login.refreshToken()))).andExpect(status().isBadRequest());
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void reusedPredecessorReturnsSafe401AndCommitsFamilyRevocation() throws Exception {
        var login = login("80000000-0000-4000-8000-000000000008", "reuse-http@example.com", "RESPONSE_BODY");
        var replacementId = uuid("a0000000-0000-4000-8000-00000000000a");
        idGenerator.setNextIds(uuid("dddddddd-dddd-4ddd-8ddd-ddddddddddd4"), replacementId, uuid("b0000000-0000-4000-8000-00000000000b"));
        var first = mockMvc
                .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"RESPONSE_BODY\"}".formatted(login.refreshToken())))
                .andExpect(status().isOk()).andReturn();
        var firstBody = JsonPath.<Map<String, Object>>read(first.getResponse().getContentAsString(), "$");
        var reuseTrace = uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5");
        idGenerator.setNextIds(reuseTrace);

        var reused = mockMvc
                .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"refreshTokenDelivery\":\"RESPONSE_BODY\"}".formatted(login.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, reuseTrace.toString()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(reused.getResponse().getContentAsString()).doesNotContain(login.refreshToken());
        assertThat(persistedState().get(1).get("revoke_reason")).isEqualTo("REUSE_DETECTED");
        assertThat(firstBody.get("accessToken")).isNotNull();
        assertThatThrownBy(() -> accessTokenConverter.convert(jwtDecoder.decode((String) firstBody.get("accessToken")))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void malformedFormAndPreflightRequestsStopBeforeRotation() throws Exception {
        var login = login("c0000000-0000-4000-8000-00000000000c", "boundary@example.com", "RESPONSE_BODY");
        var before = persistedState();
        idGenerator.setNextIds(uuid("ffffffff-ffff-4fff-8fff-fffffffffff6"), uuid("ffffffff-ffff-4fff-8fff-fffffffffff7"),
                uuid("ffffffff-ffff-4fff-8fff-fffffffffff8"), uuid("ffffffff-ffff-4fff-8fff-fffffffffff9"), uuid("ffffffff-ffff-4fff-8fff-ffffffffffa0"));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("refreshToken", login.refreshToken())
                .param("refreshTokenDelivery", "RESPONSE_BODY")).andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.TEXT_PLAIN).content(login.refreshToken()))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/api/v1/auth/refresh").param("refreshToken", login.refreshToken()).param("refreshTokenDelivery", "RESPONSE_BODY"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{not-json")).andExpect(status().isBadRequest());
        var preflight = mockMvc
                .perform(options("/api/v1/auth/refresh").header("Origin", "https://cross-origin.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(preflight.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void onlyExactRefreshPostIsPublicAndThereIsOneProductionChain() throws Exception {
        idGenerator.setNextIds(uuid("ffffffff-ffff-4fff-8fff-fffffffffff9"), uuid("ffffffff-ffff-4fff-8fff-ffffffffffa0"),
                uuid("ffffffff-ffff-4fff-8fff-ffffffffffa1"), uuid("ffffffff-ffff-4fff-8fff-ffffffffffa2"), uuid("ffffffff-ffff-4fff-8fff-ffffffffffa3"),
                uuid("ffffffff-ffff-4fff-8fff-ffffffffffa4"));
        mockMvc.perform(get("/api/v1/auth/refresh")).andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(post("/api/v1/auth/refresh/")).andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(put("/api/v1/auth/refresh")).andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(patch("/api/v1/auth/refresh")).andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(delete("/api/v1/auth/refresh")).andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(get("/api/v1/unprotected-looking-route")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
    }

    private LoginResult login(String userId, String email, String delivery) throws Exception {
        var user = uuid(userId);
        var auth = UUID.nameUUIDFromBytes((email + "-auth").getBytes(StandardCharsets.UTF_8));
        var session = UUID.nameUUIDFromBytes((email + "-session").getBytes(StandardCharsets.UTF_8));
        var access = UUID.nameUUIDFromBytes((email + "-access").getBytes(StandardCharsets.UTF_8));
        var trace = UUID.nameUUIDFromBytes((email + "-trace").getBytes(StandardCharsets.UTF_8));
        idGenerator.setNextIds(user, auth);
        registrationService.register(email, PASSWORD);
        idGenerator.setNextIds(trace, session, access);
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(
                "{\"email\":\"%s\",\"password\":\"%s\",\"deviceLabel\":\"phone\",\"refreshTokenDelivery\":\"%s\"}".formatted(email, PASSWORD, delivery)))
                .andExpect(status().isOk()).andReturn();
        var response = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        var setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        var setCookie = setCookieHeader == null ? null : HttpCookie.parse(setCookieHeader).getFirst();
        return new LoginResult(response.get("refreshToken") == null ? setCookie.getValue() : (String) response.get("refreshToken"), setCookie, setCookieHeader);
    }

    private long cookieMaxAge(String value) {
        return Long.parseLong(value.replaceAll(".*Max-Age=([0-9]+).*", "$1"));
    }

    private List<Map<String, Object>> persistedState() {
        return List.copyOf(
                jdbcTemplate.queryForList("SELECT revoked_at, revoke_reason, replaced_by_session_id FROM identity.device_session ORDER BY created_at, id"));
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(LOGIN_TIME);
        }

        @Bean
        @Primary
        RecordingIdGenerator recordingIdGenerator() {
            return new RecordingIdGenerator();
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        void setInstant(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private record LoginResult(String refreshToken, HttpCookie setCookie, String setCookieHeader) {}
}
