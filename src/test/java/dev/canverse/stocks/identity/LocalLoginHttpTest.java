package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionAuthenticationService;
import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import java.net.HttpCookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@Import(LocalLoginHttpTest.TestOverrides.class)
class LocalLoginHttpTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-15T12:00:00.750Z");
    private static final Duration REFRESH_SESSION_LIFETIME = Duration.ofHours(2);
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String WRONG_PASSWORD = "incorrect horse battery staple";
    private static final Pattern COOKIE_EXPIRES_PATTERN = Pattern.compile("(?i)(?:^|;\\s*)Expires=([^;]+)");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    DeviceSessionRepository deviceSessionRepository;

    @Autowired
    RefreshSessionAuthenticationService refreshSessionAuthenticationService;

    @Autowired
    SecureRefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    @Autowired
    ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.device_session");
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.reset();
    }

    @Test
    void responseBodyDeliveryCommitsOneExactSessionAndBindsTheAccessToken() throws Exception {
        var userId = uuid("10000000-0000-4000-8000-000000000001");
        var authIdentityId = uuid("20000000-0000-4000-8000-000000000002");
        var sessionId = uuid("30000000-0000-4000-8000-000000000003");
        var tokenId = uuid("40000000-0000-4000-8000-000000000004");
        var traceId = uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
        var email = "Alice.Login@example.com";
        var deviceLabel = "Alice's iPhone";
        registerAccount(email, userId, authIdentityId);
        var identityBeforeLogin = identitySnapshot();
        idGenerator.setNextIds(traceId, sessionId, tokenId);

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, RAW_PASSWORD, deviceLabel, "RESPONSE_BODY")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson)
                .containsOnlyKeys(
                        "sessionId",
                        "accessToken",
                        "accessTokenExpiresAt",
                        "refreshTokenExpiresAt",
                        "serverTime",
                        "refreshToken");
        assertThat(responseJson.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(responseJson.get("accessTokenExpiresAt"))
                .isEqualTo(OBSERVED_AT
                        .plus(ACCESS_TOKEN_LIFETIME)
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString());
        assertThat(responseJson.get("refreshTokenExpiresAt"))
                .isEqualTo(OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME).toString());
        assertThat(responseJson.get("serverTime")).isEqualTo(OBSERVED_AT.toString());
        var accessToken = (String) responseJson.get("accessToken");
        var refreshToken = (String) responseJson.get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(response).doesNotContain(email, RAW_PASSWORD);
        assertThat(idGenerator.consumedIds()).startsWith(traceId, sessionId, tokenId);
        assertNoServletSession(result);

        var decodedAccessToken = jwtDecoder.decode(accessToken);
        assertThat(decodedAccessToken.getSubject()).isEqualTo(userId.toString());
        assertThat(decodedAccessToken.getClaimAsString("sid")).isEqualTo(sessionId.toString());

        var persistedSession = persistedSession(sessionId);
        assertThat(deviceSessionRepository.count()).isOne();
        assertThat(persistedSession.userAccountId()).isEqualTo(userId);
        assertThat(persistedSession.familyId()).isEqualTo(sessionId);
        assertThat(persistedSession.deviceLabel()).isEqualTo(deviceLabel);
        assertThat(persistedSession.createdAt()).isEqualTo(OBSERVED_AT);
        assertThat(persistedSession.expiresAt()).isEqualTo(OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME));
        assertThat(persistedSession.lastUsedAt()).isNull();
        assertThat(persistedSession.revokedAt()).isNull();
        assertThat(persistedSession.revokeReason()).isNull();
        assertThat(persistedSession.replacedBySessionId()).isNull();
        assertThat(persistedSession.refreshTokenHash())
                .isEqualTo(refreshTokenGenerator.hash(refreshToken))
                .isNotEqualTo(refreshToken);
        assertThat(rawTokenTextColumnOccurrences(refreshToken)).isZero();
        assertThat(refreshSessionAuthenticationService.authenticate(refreshToken))
                .isEqualTo(sessionId);
        assertThat(identitySnapshot()).isEqualTo(identityBeforeLogin);
        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();
    }

    @Test
    void httpOnlyCookieDeliveryUsesOnlyTheCookieChannel() throws Exception {
        var userId = uuid("50000000-0000-4000-8000-000000000005");
        var authIdentityId = uuid("60000000-0000-4000-8000-000000000006");
        var sessionId = uuid("70000000-0000-4000-8000-000000000007");
        var tokenId = uuid("80000000-0000-4000-8000-000000000008");
        var traceId = uuid("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
        var email = "cookie-login@example.com";
        registerAccount(email, userId, authIdentityId);
        var identityBeforeLogin = identitySnapshot();
        idGenerator.setNextIds(traceId, sessionId, tokenId);

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, RAW_PASSWORD, null, "HTTP_ONLY_COOKIE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson)
                .containsOnlyKeys(
                        "sessionId", "accessToken", "accessTokenExpiresAt", "refreshTokenExpiresAt", "serverTime");
        assertThat(responseJson).doesNotContainKey("refreshToken");
        assertThat(responseJson.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(responseJson.get("accessTokenExpiresAt"))
                .isEqualTo(OBSERVED_AT
                        .plus(ACCESS_TOKEN_LIFETIME)
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString());
        assertThat(responseJson.get("refreshTokenExpiresAt"))
                .isEqualTo(OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME).toString());
        assertThat(responseJson.get("serverTime")).isEqualTo(OBSERVED_AT.toString());
        assertThat(response).doesNotContain(RAW_PASSWORD, email);
        var setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).hasSize(1);
        var setCookie = setCookieHeaders.getFirst();
        assertThat(setCookie)
                .contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
        var cookie = HttpCookie.parse(setCookie).getFirst();
        assertThat(cookie.getName()).isEqualTo("refresh-token");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_SESSION_LIFETIME.getSeconds());
        assertThat(cookie.getMaxAge()).isPositive();
        var cookieExpiresAt = cookieExpiresAt(setCookie);
        assertThat(cookieExpiresAt.getNano()).isZero();
        assertThat(cookieExpiresAt)
                .isEqualTo(OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME).truncatedTo(ChronoUnit.SECONDS));
        assertNoServletSession(result);
        assertThat(idGenerator.consumedIds()).startsWith(traceId, sessionId, tokenId);

        var persistedSession = persistedSession(sessionId);
        var refreshToken = cookie.getValue();
        assertThat(persistedSession.deviceLabel()).isNull();
        assertThat(persistedSession.expiresAt()).isEqualTo(OBSERVED_AT.plus(REFRESH_SESSION_LIFETIME));
        assertThat(persistedSession.refreshTokenHash())
                .isEqualTo(refreshTokenGenerator.hash(refreshToken))
                .isNotEqualTo(refreshToken);
        assertThat(rawTokenTextColumnOccurrences(refreshToken)).isZero();
        assertThat(refreshSessionAuthenticationService.authenticate(refreshToken))
                .isEqualTo(sessionId);
        assertThat(identitySnapshot()).isEqualTo(identityBeforeLogin);
        assertThat(deviceSessionRepository.count()).isOne();
    }

    @Test
    void credentialFailuresAreUniformAndDeliverNoTokenOrCookie() throws Exception {
        var userId = uuid("90000000-0000-4000-8000-000000000009");
        var authIdentityId = uuid("a0000000-0000-4000-8000-00000000000a");
        var email = "known-login@example.com";
        registerAccount(email, userId, authIdentityId);
        var beforeFailures = persistedState();

        var unknownEmail = "unknown-login@example.com";
        var unknownTraceId = uuid("cccccccc-cccc-4ccc-8ccc-ccccccccccc1");
        idGenerator.setNextIds(unknownTraceId);
        var unknownResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson(unknownEmail, RAW_PASSWORD, "unknown device", "RESPONSE_BODY")))
                .andReturn();
        assertInvalidCredentials(unknownResult, unknownTraceId, unknownEmail, RAW_PASSWORD);
        assertThat(idGenerator.consumedIds()).startsWith(unknownTraceId);
        assertThat(persistedState()).isEqualTo(beforeFailures);

        var wrongTraceId = uuid("cccccccc-cccc-4ccc-8ccc-ccccccccccc2");
        idGenerator.setNextIds(wrongTraceId);
        var wrongResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, WRONG_PASSWORD, "wrong device", "HTTP_ONLY_COOKIE")))
                .andReturn();
        assertInvalidCredentials(wrongResult, wrongTraceId, email, WRONG_PASSWORD);
        assertThat(idGenerator.consumedIds()).startsWith(wrongTraceId);
        assertThat(persistedState()).isEqualTo(beforeFailures);
    }

    @Test
    void acceptedEmailAndPasswordBoundariesReachCredentialWorkflow() throws Exception {
        var maximumEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + "."
                + "e".repeat(63);
        var minimumPasswordTraceId = uuid("dddddddd-dddd-4ddd-8ddd-ddddddddddd1");
        idGenerator.setNextIds(minimumPasswordTraceId);
        var minimumPasswordResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson(maximumEmail, "p".repeat(12), null, "RESPONSE_BODY")))
                .andReturn();
        assertInvalidCredentials(minimumPasswordResult, minimumPasswordTraceId, maximumEmail, "p".repeat(12));
        assertThat(idGenerator.consumedIds()).startsWith(minimumPasswordTraceId);

        var maximumPasswordTraceId = uuid("dddddddd-dddd-4ddd-8ddd-ddddddddddd2");
        idGenerator.setNextIds(maximumPasswordTraceId);
        var maximumPassword = "p".repeat(128);
        var maximumPasswordResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(loginJson("boundary@example.com", maximumPassword, null, "RESPONSE_BODY")))
                .andReturn();
        assertInvalidCredentials(
                maximumPasswordResult, maximumPasswordTraceId, "boundary@example.com", maximumPassword);
        assertThat(idGenerator.consumedIds()).startsWith(maximumPasswordTraceId);
        assertThat(deviceSessionRepository.count()).isZero();
    }

    @Test
    void requestValidationAndParsingStopBeforeLoginWorkflow() throws Exception {
        var beforeFailures = persistedState();
        assertValidationFailure(
                loginJson("", RAW_PASSWORD, null, "RESPONSE_BODY"),
                "email",
                "error.fields.common.not_blank",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("not-an-email", RAW_PASSWORD, null, "RESPONSE_BODY"),
                "email",
                "error.fields.common.email",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson(" alice@example.com", RAW_PASSWORD, null, "RESPONSE_BODY"),
                "email",
                "error.fields.common.pattern",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee3"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("alice@example.com ", RAW_PASSWORD, null, "RESPONSE_BODY"),
                "email",
                "error.fields.common.pattern",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee4"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("a".repeat(310) + "@example.com", RAW_PASSWORD, null, "RESPONSE_BODY"),
                "email",
                "error.fields.common.size",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("alice@example.com", "", null, "RESPONSE_BODY"),
                "password",
                "error.fields.common.not_blank",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee6"),
                beforeFailures);
        assertValidationFailure(
                loginJson("alice@example.com", "p".repeat(11), null, "RESPONSE_BODY"),
                "password",
                "error.fields.common.size",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee7"),
                beforeFailures,
                "p".repeat(11));
        var overlongPassword = "p".repeat(129);
        assertValidationFailure(
                loginJson("alice@example.com", overlongPassword, null, "RESPONSE_BODY"),
                "password",
                "error.fields.common.size",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee8"),
                beforeFailures,
                overlongPassword);
        assertValidationFailure(
                loginJson("alice@example.com", RAW_PASSWORD, " ", "RESPONSE_BODY"),
                "deviceLabel",
                "error.fields.common.pattern",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee9"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("alice@example.com", RAW_PASSWORD, " device", "RESPONSE_BODY"),
                "deviceLabel",
                "error.fields.common.pattern",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeea"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("alice@example.com", RAW_PASSWORD, "device ", "RESPONSE_BODY"),
                "deviceLabel",
                "error.fields.common.pattern",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeeb"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                loginJson("alice@example.com", RAW_PASSWORD, "d".repeat(129), "RESPONSE_BODY"),
                "deviceLabel",
                "error.fields.common.size",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeec"),
                beforeFailures,
                RAW_PASSWORD);
        assertValidationFailure(
                "{\"email\":\"alice@example.com\",\"password\":\"" + RAW_PASSWORD + "\",\"deviceLabel\":null}",
                "refreshTokenDelivery",
                "error.fields.common.not_null",
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeed"),
                beforeFailures,
                RAW_PASSWORD);
        assertMalformedRequest(
                loginJson("alice@example.com", RAW_PASSWORD, null, "UNSUPPORTED"),
                uuid("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"),
                beforeFailures,
                RAW_PASSWORD,
                "UNSUPPORTED");
        assertMalformedRequest(
                "{\"email\":\"alice@example.com\",\"password\":\"" + RAW_PASSWORD,
                uuid("ffffffff-ffff-4fff-8fff-fffffffffff1"),
                beforeFailures,
                "alice@example.com",
                RAW_PASSWORD);
    }

    @Test
    void onlyTheExactLoginPostIsPublicAndThereIsOneProductionChain() throws Exception {
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        var traceId = uuid("ffffffff-ffff-4fff-8fff-fffffffffff2");
        idGenerator.setNextIds(traceId);

        var result = mockMvc.perform(get("/api/v1/auth/login")).andReturn();

        assertInvalidCredentials(result, traceId, null, null, true);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId);
    }

    private void registerAccount(String email, UUID userId, UUID authIdentityId) {
        idGenerator.setNextIds(userId, authIdentityId);
        assertThat(registrationService.register(email, RAW_PASSWORD)).isEqualTo(userId);
    }

    private void assertInvalidCredentials(
            MvcResult result, UUID expectedTraceId, String submittedEmail, String submittedPassword) throws Exception {
        assertInvalidCredentials(result, expectedTraceId, submittedEmail, submittedPassword, false);
    }

    private void assertInvalidCredentials(
            MvcResult result,
            UUID expectedTraceId,
            String submittedEmail,
            String submittedPassword,
            boolean bearerChallenge)
            throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (bearerChallenge) {
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        } else {
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        }
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isEqualTo(expectedTraceId.toString());
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        var body = response.getContentAsString();
        var problem = JsonPath.<Map<String, Object>>read(body, "$");
        assertThat(problem)
                .containsOnlyKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp")
                .containsEntry("type", "https://canverse.dev/problems/invalid-credentials")
                .containsEntry("title", "Unauthorized")
                .containsEntry("status", 401)
                .containsEntry("instance", "/api/v1/auth/login")
                .containsEntry("code", "INVALID_CREDENTIALS")
                .containsEntry("key", "error.identity.invalid_credentials")
                .containsEntry("traceId", expectedTraceId.toString());
        assertThat(Instant.parse(problem.get("timestamp").toString())).isEqualTo(OBSERVED_AT);
        if (submittedEmail != null) {
            assertThat(body).doesNotContain(submittedEmail);
        }
        if (submittedPassword != null) {
            assertThat(body).doesNotContain(submittedPassword);
        }
        assertNoServletSession(result);
    }

    private void assertValidationFailure(
            String requestBody,
            String expectedField,
            String expectedKey,
            UUID traceId,
            PersistedState expectedState,
            String... sensitiveValues)
            throws Exception {
        idGenerator.setNextIds(traceId);
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var body = result.getResponse().getContentAsString();
        var problem = JsonPath.<Map<String, Object>>read(body, "$");
        assertThat(problem.get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.get("key")).isEqualTo("error.common.validation_failed");
        var keys = JsonPath.<List<String>>read(body, "$.params.errors[?(@.field == '" + expectedField + "')].key");
        assertThat(keys).contains(expectedKey);
        assertTraceCorrelation(result, traceId);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId);
        assertThat(persistedState()).isEqualTo(expectedState);
        assertNoServletSession(result);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        if (sensitiveValues.length > 0) {
            assertThat(body).doesNotContain(sensitiveValues);
        }
    }

    private void assertMalformedRequest(
            String requestBody, UUID traceId, PersistedState expectedState, String... sensitiveValues)
            throws Exception {
        idGenerator.setNextIds(traceId);
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var body = result.getResponse().getContentAsString();
        var problem = JsonPath.<Map<String, Object>>read(body, "$");
        assertThat(problem.get("code")).isEqualTo("MALFORMED_REQUEST");
        assertTraceCorrelation(result, traceId);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId);
        assertThat(persistedState()).isEqualTo(expectedState);
        assertNoServletSession(result);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(body).doesNotContain(sensitiveValues);
    }

    private void assertTraceCorrelation(MvcResult result, UUID expectedTraceId) throws Exception {
        var headerTraceId = result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER);
        var bodyTraceId = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(headerTraceId).isEqualTo(expectedTraceId.toString());
        assertThat(bodyTraceId).isEqualTo(headerTraceId);
    }

    private void assertNoServletSession(MvcResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("JSESSIONID="));
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private PersistedSession persistedSession(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            DeviceSession session = deviceSessionRepository.findById(sessionId).orElseThrow();
            return new PersistedSession(
                    session.getId(),
                    session.getUserAccount().getId(),
                    session.getFamilyId(),
                    session.getRefreshTokenHash(),
                    session.getDeviceLabel(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    session.getExpiresAt(),
                    session.getRevokedAt(),
                    session.getRevokeReason(),
                    session.getReplacedBySessionId());
        });
    }

    private PersistedState persistedState() {
        return new PersistedState(
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.device_session ORDER BY id")));
    }

    private IdentityState identitySnapshot() {
        return new IdentityState(
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.user_account ORDER BY id")),
                List.copyOf(jdbcTemplate.queryForList("SELECT * FROM identity.auth_identity ORDER BY id")));
    }

    private long rawTokenTextColumnOccurrences(String rawToken) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.device_session"
                        + " WHERE strpos(refresh_token_hash, ?) > 0"
                        + " OR strpos(coalesce(device_label, ''), ?) > 0"
                        + " OR strpos(coalesce(revoke_reason, ''), ?) > 0",
                Long.class,
                rawToken,
                rawToken,
                rawToken);
    }

    private Instant cookieExpiresAt(String setCookie) {
        var matcher = COOKIE_EXPIRES_PATTERN.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return ZonedDateTime.parse(matcher.group(1), DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant();
    }

    private String loginJson(String email, String password, String deviceLabel, String refreshTokenDelivery) {
        var labelJson = deviceLabel == null ? "null" : "\"" + deviceLabel + "\"";
        return "{\"email\":\"%s\",\"password\":\"%s\",\"deviceLabel\":%s,\"refreshTokenDelivery\":\"%s\"}"
                .formatted(email, password, labelJson, refreshTokenDelivery);
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
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

    static final class RecordingIdGenerator implements IdGenerator {

        private final Deque<UUID> nextIds = new ArrayDeque<>();
        private final Deque<UUID> consumedIds = new ArrayDeque<>();

        void setNextIds(UUID... ids) {
            nextIds.clear();
            consumedIds.clear();
            nextIds.addAll(Arrays.asList(ids));
        }

        void reset() {
            setNextIds();
        }

        Deque<UUID> consumedIds() {
            return new ArrayDeque<>(consumedIds);
        }

        @Override
        public UUID next() {
            var nextId = nextIds.isEmpty() ? UUID.randomUUID() : nextIds.removeFirst();
            consumedIds.addLast(nextId);
            return nextId;
        }
    }

    private record PersistedSession(
            UUID id,
            UUID userAccountId,
            UUID familyId,
            String refreshTokenHash,
            String deviceLabel,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokeReason,
            UUID replacedBySessionId) {}

    private record IdentityState(List<Map<String, Object>> users, List<Map<String, Object>> identities) {}

    private record PersistedState(
            List<Map<String, Object>> users,
            List<Map<String, Object>> identities,
            List<Map<String, Object>> sessions) {}
}
