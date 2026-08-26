package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
@Import(FinancialAccountHttpTest.TestOverrides.class)
@Execution(ExecutionMode.SAME_THREAD)
class FinancialAccountHttpTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T12:00:00Z");

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

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.execute(
                        "TRUNCATE TABLE ledger.money_posting, ledger.activity, ledger.account_balance_projection,"
                                + " ledger.account_cash_pocket, ledger.idempotency_record, ledger.financial_account,"
                                + " identity.device_session, identity.auth_identity, identity.user_account CASCADE"));
    }

    @Test
    void authenticatedOwnerCanCreateReadBalanceUpdateAndArchiveAnAccount() throws Exception {
        var owner = authenticated("account-http-owner@example.com");
        var accountRequestId = uuid("10000000-0000-4000-8000-000000000001");
        var created = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(accountRequestId, "Operating cash", "100")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.kind", equalTo("CASH_CURRENT")))
                .andExpect(jsonPath("$.trackingMode", equalTo("FULL_LEDGER")))
                .andExpect(jsonPath("$.currency", equalTo("USD")))
                .andExpect(jsonPath("$.cashCoverageStatus", equalTo("KNOWN_FROM_OPENING")))
                .andExpect(jsonPath("$.version", equalTo(1)))
                .andReturn();
        var accountId = idFrom(created);
        assertThat(URI.create(created.getResponse().getHeader(HttpHeaders.LOCATION))
                        .getPath())
                .isEqualTo("/api/v1/accounts/" + accountId);

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(accountId.toString())))
                .andExpect(jsonPath("$.name", equalTo("Operating cash")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.ledgerBalance", equalTo("100")))
                .andExpect(jsonPath("$.clearedBalance", equalTo("100")))
                .andExpect(jsonPath("$.cashHeld", equalTo("100")))
                .andExpect(jsonPath("$.nativeCurrency", equalTo("USD")))
                .andExpect(jsonPath("$.projectionStatus", equalTo("CURRENT")));

        var corrected = mockMvc.perform(put("/api/v1/accounts/{accountId}/opening-state", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openingCorrectionJson(
                                uuid("10000000-0000-4000-8000-000000000002"), 1, "110", "Opening correction")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(2)))
                .andReturn();

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("110")));

        var updated = mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metadataJson(
                                uuid("10000000-0000-4000-8000-000000000003"),
                                correctedVersion(corrected),
                                " Operating cash ",
                                "Europe/London")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Operating cash")))
                .andExpect(jsonPath("$.timeZone", equalTo("Europe/London")))
                .andExpect(jsonPath("$.version", equalTo(3)))
                .andReturn();

        var policy = mockMvc.perform(put("/api/v1/accounts/{accountId}/policy", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(uuid("10000000-0000-4000-8000-000000000004"), 3, "SOFT_FLOOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy", equalTo("SOFT_FLOOR")))
                .andExpect(jsonPath("$.version", equalTo(4)))
                .andReturn();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(archiveJson(
                                uuid("10000000-0000-4000-8000-000000000005"),
                                JsonPath.<Integer>read(policy.getResponse().getContentAsString(), "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived", equalTo(true)))
                .andExpect(jsonPath("$.version", equalTo(5)));

        mockMvc.perform(get("/api/v1/accounts")
                        .param("includeArchived", "false")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", equalTo(0)))
                .andExpect(jsonPath("$.accounts").doesNotExist());
        mockMvc.perform(get("/api/v1/accounts")
                        .param("includeArchived", "true")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id", equalTo(accountId.toString())));
        assertNoSession(created);
    }

    @Test
    void holdingsOnlyBrokerageReturnsUntrackedCashAndCrossOwnerIdsStayNotFound() throws Exception {
        var owner = authenticated("account-http-holdings-owner@example.com");
        var other = authenticated("account-http-other-owner@example.com");
        var created = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdingsOnlyJson(uuid("20000000-0000-4000-8000-000000000001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind", equalTo("BROKERAGE")))
                .andExpect(jsonPath("$.trackingMode", equalTo("HOLDINGS_ONLY")))
                .andExpect(jsonPath("$.cashCoverageStatus", equalTo("UNTRACKED")))
                .andReturn();
        var accountId = idFrom(created);

        var crossOwner = mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")))
                .andReturn();
        assertProblemShape(crossOwner);
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
    }

    @Test
    void knownAccountNameConstraintUsesTheStableLedgerErrorCode() throws Exception {
        var owner = authenticated("account-http-constraint-owner@example.com");
        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("21000000-0000-4000-8000-000000000001"), "Unique cash", "1")))
                .andExpect(status().isCreated());

        var duplicate = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("21000000-0000-4000-8000-000000000002"), " Unique cash ", "2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NAME_CONFLICT")))
                .andReturn();
        assertProblemShape(duplicate);
        assertThat(duplicate.getResponse().getContentAsString())
                .doesNotContain("uix_ledger_financial_account_active_name");
    }

    @Test
    void accountListReturnsOwnerScopedDirectOrderedCollection() throws Exception {
        var owner = authenticated("account-http-list-owner@example.com");
        var other = authenticated("account-http-list-other-owner@example.com");
        var archivedAlpha = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("22000000-0000-4000-8000-000000000001"), "Alpha cash", "1")))
                .andExpect(status().isCreated())
                .andReturn();
        var archivedAlphaId = idFrom(archivedAlpha);
        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", archivedAlphaId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(archiveJson(uuid("22000000-0000-4000-8000-000000000002"), 1)))
                .andExpect(status().isOk());

        var activeAlpha = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("22000000-0000-4000-8000-000000000003"), "alpha cash", "1")))
                .andExpect(status().isCreated())
                .andReturn();
        var activeAlphaId = idFrom(activeAlpha);
        var betaId = idFrom(mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("22000000-0000-4000-8000-000000000004"), "Beta cash", "1")))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("22000000-0000-4000-8000-000000000005"), "Aardvark cash", "1")))
                .andExpect(status().isCreated());

        var active = mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", equalTo(2)))
                .andReturn();
        assertThat(JsonPath.<List<String>>read(active.getResponse().getContentAsString(), "$[*].id"))
                .containsExactly(activeAlphaId.toString(), betaId.toString());

        var all = mockMvc.perform(get("/api/v1/accounts")
                        .param("includeArchived", "true")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();
        var allIds = JsonPath.<List<String>>read(all.getResponse().getContentAsString(), "$[*].id");
        assertThat(allIds.subList(0, 2))
                .containsExactlyElementsOf(List.of(archivedAlphaId.toString(), activeAlphaId.toString()).stream()
                        .sorted()
                        .toList());
        assertThat(allIds.get(2)).isEqualTo(betaId.toString());
        assertThat(all.getResponse().getContentAsString())
                .doesNotContain("\"accounts\"", "\"page\"", "\"size\"", "\"hasNext\"");
    }

    @Test
    void missingBearerAndInvalidOpeningContractAreRejectedWithoutLedgerWrites() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        var owner = authenticated("account-http-validation-owner@example.com");
        var result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30000000-0000-4000-8000-000000000001","name":"Missing opening","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andReturn();
        assertProblemShape(result);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class))
                .isZero();

        var offsetZone = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30000000-0000-4000-8000-000000000002","name":"Offset zone","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"+02:00","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andReturn();
        assertProblemShape(offsetZone);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class))
                .isZero();

        var futureOpening = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30000000-0000-4000-8000-000000000003","name":"Future opening","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-18T12:00:00Z"}}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andReturn();
        assertProblemShape(futureOpening);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class))
                .isZero();
    }

    @Test
    void inactiveCurrencyAndUnknownCurrencyUseTheStableApplicationError() throws Exception {
        var owner = authenticated("account-http-currency-owner@example.com");
        setCurrencyActive("EUR", false);
        try {
            mockMvc.perform(post("/api/v1/accounts")
                            .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientRequestId":"30500000-0000-4000-8000-000000000001","name":"Inactive currency","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"EUR","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code", equalTo("ACCOUNT_CURRENCY_UNSUPPORTED")));
        } finally {
            setCurrencyActive("EUR", true);
        }

        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30500000-0000-4000-8000-000000000002","name":"Unknown currency","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"ZZZ","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_CURRENCY_UNSUPPORTED")));
    }

    @Test
    void mutationVersionsAreRequiredAndNonNegativeAtTheHttpBoundary() throws Exception {
        var owner = authenticated("account-http-version-validation-owner@example.com");
        var account = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("31000000-0000-4000-8000-000000000001"), "Versioned cash", "10")))
                .andExpect(status().isCreated())
                .andReturn();
        var accountId = idFrom(account);

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metadataJson(uuid("31000000-0000-4000-8000-000000000007"), 0, "Stale version", "UTC")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_VERSION_CONFLICT")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"31000000-0000-4000-8000-000000000002","name":"Renamed","timeZone":"UTC"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}/policy", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"31000000-0000-4000-8000-000000000003","policy":"HARD_FLOOR"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"31000000-0000-4000-8000-000000000004"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}/opening-state", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"31000000-0000-4000-8000-000000000005","amount":"11","effectiveAt":"2026-08-17T11:00:00Z","correctionReason":"Missing version"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"31000000-0000-4000-8000-000000000006","version":-1,"name":"Negative version","timeZone":"UTC"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    private Identity authenticated(String email) {
        var userId = registrationService.register(email, PASSWORD);
        var session = sessionIssuanceService.issue(userId, "ledger-http-test");
        return new Identity(
                userId, tokenIssuanceService.issue(session.sessionId()).accessToken());
    }

    private static String createJson(UUID requestId, String name, String amount) {
        return """
                {"clientRequestId":"%s","name":"%s","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"%s","effectiveAt":"2026-08-17T11:00:00Z"}}
                """.formatted(requestId, name, amount);
    }

    private static String holdingsOnlyJson(UUID requestId) {
        return """
                {"clientRequestId":"%s","name":"Brokerage holdings","kind":"BROKERAGE","trackingMode":"HOLDINGS_ONLY","currency":"USD","timeZone":"UTC"}
                """.formatted(requestId);
    }

    private static String metadataJson(UUID requestId, long version, String name, String timeZone) {
        return """
                {"clientRequestId":"%s","version":%d,"name":"%s","timeZone":"%s"}
                """.formatted(requestId, version, name, timeZone);
    }

    private static String openingCorrectionJson(UUID requestId, long version, String amount, String reason) {
        return """
                {"clientRequestId":"%s","version":%d,"amount":"%s","effectiveAt":"2026-08-17T11:00:00Z","correctionReason":"%s"}
                """.formatted(requestId, version, amount, reason);
    }

    private static String policyJson(UUID requestId, long version, String policy) {
        return """
                {"clientRequestId":"%s","version":%d,"policy":"%s"}
                """.formatted(requestId, version, policy);
    }

    private static int correctedVersion(MvcResult result) throws Exception {
        return JsonPath.<Integer>read(result.getResponse().getContentAsString(), "$.version");
    }

    private static String archiveJson(UUID requestId, long version) {
        return "{\"clientRequestId\":\"%s\",\"version\":%d}".formatted(requestId, version);
    }

    private void setCurrencyActive(String currency, boolean active) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(
                        "UPDATE reference.currency SET active = ? WHERE code = ?", active, currency));
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static void assertProblemShape(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body).containsKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isNotBlank();
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record Identity(UUID userId, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(OBSERVED_AT, ZoneOffset.UTC);
        }
    }
}
