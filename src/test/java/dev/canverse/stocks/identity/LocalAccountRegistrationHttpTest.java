package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(LocalAccountRegistrationHttpTest.TestOverrides.class)
class LocalAccountRegistrationHttpTest {

    private static final Instant REGISTRATION_TIME = Instant.parse("2026-08-08T12:34:56Z");
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    RequestTraceFilter requestTraceFilter;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AuthIdentityRepository authIdentityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordingIdGenerator idGenerator;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestTraceFilter)
                .build();
        runInTransaction(() -> {
            jdbcTemplate.update("DELETE FROM identity.auth_identity");
            jdbcTemplate.update("DELETE FROM identity.user_account");
        });
        idGenerator.reset();
    }

    @Test
    void successfulHttpRegistrationCommitsUserAndLocalIdentity() throws Exception {
        var traceId = uuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var userId = uuid("10000000-0000-0000-0000-000000000001");
        var authIdentityId = uuid("20000000-0000-0000-0000-000000000002");
        idGenerator.setNextIds(traceId, userId, authIdentityId);

        var result = performRegistration("Alice.Example@Example.COM", RAW_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson).containsOnlyKeys("userId");
        assertThat(responseJson.get("userId")).isEqualTo(userId.toString());
        assertThat(response).doesNotContain(RAW_PASSWORD);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId, userId, authIdentityId);

        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();
        var userAccount = userAccountRepository.findById(userId).orElseThrow();
        var authIdentity = authIdentityRepository.findById(authIdentityId).orElseThrow();
        assertThat(userAccount.getEmail()).isEqualTo("Alice.Example@Example.COM");
        assertThat(userAccount.getEmailNormalized()).isEqualTo("alice.example@example.com");
        assertThat(userAccount.getCreatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(userAccount.getUpdatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(authIdentity.getProvider()).isEqualTo("LOCAL");
        assertThat(authIdentity.getProviderSubject()).isEqualTo("alice.example@example.com");
        assertThat(authIdentity.getCreatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(authIdentity.getUpdatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT user_account_id FROM identity.auth_identity WHERE id = ?", UUID.class, authIdentityId))
                .isEqualTo(userId);
        assertThat(authIdentity.getPasswordHash()).isNotEqualTo(RAW_PASSWORD).startsWith("{");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, authIdentity.getPasswordHash()))
                .isTrue();
        assertThat(response).doesNotContain(authIdentity.getPasswordHash());
    }

    @Test
    void duplicateEmailThroughHttpReturnsSafeConflictWithoutNewRows() throws Exception {
        var firstTraceId = uuid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var userId = uuid("30000000-0000-0000-0000-000000000003");
        var authIdentityId = uuid("40000000-0000-0000-0000-000000000004");
        idGenerator.setNextIds(firstTraceId, userId, authIdentityId);

        performRegistration("Alice.Example@Example.COM", RAW_PASSWORD).andExpect(status().isCreated());
        var firstUserAccount = userAccountRepository.findById(userId).orElseThrow();
        var firstAuthIdentity = authIdentityRepository.findById(authIdentityId).orElseThrow();

        var retryTraceId = uuid("cccccccc-cccc-cccc-cccc-cccccccccccc");
        idGenerator.setNextIds(retryTraceId);
        var result = performRegistration("ALICE.EXAMPLE@example.com", RAW_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, retryTraceId.toString()))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson.get("code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(responseJson.get("key")).isEqualTo("error.identity.email_already_registered");
        assertThat(responseJson).doesNotContainKey("params");
        assertThat(response)
                .doesNotContain(
                        "ALICE.EXAMPLE@example.com",
                        "alice.example@example.com",
                        RAW_PASSWORD,
                        "uq_user_account_email_normalized",
                        "uq_auth_identity_provider_subject",
                        "constraint");
        assertTraceCorrelation(result, retryTraceId);
        assertThat(idGenerator.consumedIds()).containsExactly(retryTraceId);

        assertThat(userAccountRepository.count()).isOne();
        assertThat(authIdentityRepository.count()).isOne();
        var persistedUserAccount = userAccountRepository.findById(userId).orElseThrow();
        var persistedAuthIdentity =
                authIdentityRepository.findById(authIdentityId).orElseThrow();
        assertThat(persistedUserAccount.getEmail()).isEqualTo(firstUserAccount.getEmail());
        assertThat(persistedUserAccount.getEmailNormalized()).isEqualTo(firstUserAccount.getEmailNormalized());
        assertThat(persistedAuthIdentity.getProviderSubject()).isEqualTo(firstAuthIdentity.getProviderSubject());
        assertThat(persistedAuthIdentity.getPasswordHash()).isEqualTo(firstAuthIdentity.getPasswordHash());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT user_account_id FROM identity.auth_identity WHERE id = ?", UUID.class, authIdentityId))
                .isEqualTo(userId);
    }

    @Test
    void invalidHttpRegistrationIsRejectedBeforeIdentityWrites() throws Exception {
        assertValidationFailure(
                "",
                RAW_PASSWORD,
                "email",
                "error.fields.common.not_blank",
                uuid("d0000000-0000-0000-0000-000000000001"));
        assertValidationFailure(
                "not-an-email",
                RAW_PASSWORD,
                "email",
                "error.fields.common.email",
                uuid("d0000000-0000-0000-0000-000000000002"));
        assertValidationFailure(
                " alice@example.com",
                RAW_PASSWORD,
                "email",
                "error.fields.common.pattern",
                uuid("d0000000-0000-0000-0000-000000000003"));
        assertValidationFailure(
                "alice@example.com ",
                RAW_PASSWORD,
                "email",
                "error.fields.common.pattern",
                uuid("d0000000-0000-0000-0000-000000000004"));
        assertValidationFailure(
                "a".repeat(310) + "@example.com",
                RAW_PASSWORD,
                "email",
                "error.fields.common.size",
                uuid("d0000000-0000-0000-0000-000000000005"));
        assertValidationFailure(
                "alice@example.com",
                "",
                "password",
                "error.fields.common.not_blank",
                uuid("d0000000-0000-0000-0000-000000000006"));
        assertValidationFailure(
                "alice@example.com",
                "p".repeat(11),
                "password",
                "error.fields.common.size",
                uuid("d0000000-0000-0000-0000-000000000007"));
        var overlongPassword = "p".repeat(129);
        assertValidationFailure(
                "alice@example.com",
                overlongPassword,
                "password",
                "error.fields.common.size",
                uuid("d0000000-0000-0000-0000-000000000008"));

        assertThat(userAccountRepository.count()).isZero();
        assertThat(authIdentityRepository.count()).isZero();
    }

    @Test
    void malformedHttpRequestUsesSafeProblemContractWithoutWrites() throws Exception {
        var traceId = uuid("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        idGenerator.setNextIds(traceId);

        var submittedFragment = "{\"email\":\"Alice.Example@example.com\",\"password\":\"" + RAW_PASSWORD;
        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(submittedFragment))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson.get("code")).isEqualTo("MALFORMED_REQUEST");
        assertThat(response)
                .doesNotContain(
                        "Alice.Example@example.com", RAW_PASSWORD, "JsonParseException", "Unexpected end-of-input");
        assertTraceCorrelation(result, traceId);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId);
        assertThat(userAccountRepository.count()).isZero();
        assertThat(authIdentityRepository.count()).isZero();
    }

    private ResultActions performRegistration(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(requestJson(email, password)));
    }

    private void assertValidationFailure(
            String email, String password, String expectedField, String expectedKey, UUID traceId) throws Exception {
        idGenerator.setNextIds(traceId);
        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestJson(email, password)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, traceId.toString()))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        var responseJson = JsonPath.<Map<String, Object>>read(response, "$");
        assertThat(responseJson.get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(responseJson.get("key")).isEqualTo("error.common.validation_failed");
        assertValidationKey(response, expectedField, expectedKey);
        if (!password.isEmpty()) {
            assertThat(response).doesNotContain(password);
        }
        assertTraceCorrelation(result, traceId);
        assertThat(idGenerator.consumedIds()).containsExactly(traceId);
        assertThat(userAccountRepository.count()).isZero();
        assertThat(authIdentityRepository.count()).isZero();
    }

    private String requestJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private void assertValidationKey(String response, String field, String expectedKey) {
        List<String> keys = JsonPath.read(response, "$.params.errors[?(@.field == '" + field + "')].key");
        assertThat(keys).contains(expectedKey);
    }

    private void assertTraceCorrelation(MvcResult result, UUID expectedTraceId) throws Exception {
        var headerTraceId = result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER);
        var bodyTraceId = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(headerTraceId).isEqualTo(expectedTraceId.toString());
        assertThat(bodyTraceId).isEqualTo(headerTraceId);
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
            return Clock.fixed(REGISTRATION_TIME, ZoneOffset.UTC);
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

        @Override
        public UUID next() {
            var nextId = nextIds.isEmpty() ? UUID.randomUUID() : nextIds.removeFirst();
            consumedIds.addLast(nextId);
            return nextId;
        }

        Deque<UUID> consumedIds() {
            return new ArrayDeque<>(consumedIds);
        }
    }
}
