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
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.HttpAssertions;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import dev.canverse.stocks.testing.TestIdentitySupport;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"stocks.identity.refresh-session.lifetime=2h"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class FinancialAccountHttpTest {

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    TestIdentitySupport testIdentitySupport;
    @Autowired
    TestClock testClock;
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T12:00:00Z");
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        testClock.setInstant(OBSERVED_AT);

        databaseCleaner.resetApplicationState();
    }

    @Test
    void authenticatedOwnerCanCreateReadBalanceUpdateAndArchiveAnAccount() throws Exception {
        var owner = testIdentitySupport.create("account-http-owner@example.com");
        var accountRequestId = uuid("10000000-0000-4000-8000-000000000001");
        var created = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(accountRequestId, "Operating cash", "100")))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache")).andExpect(jsonPath("$.kind", equalTo("CASH_CURRENT")))
                .andExpect(jsonPath("$.trackingMode", equalTo("FULL_LEDGER"))).andExpect(jsonPath("$.currency", equalTo("USD")))
                .andExpect(jsonPath("$.cashCoverageStatus", equalTo("KNOWN_FROM_OPENING"))).andExpect(jsonPath("$.version", equalTo(1))).andReturn();
        var accountId = idFrom(created);
        assertThat(URI.create(created.getResponse().getHeader(HttpHeaders.LOCATION)).getPath()).isEqualTo("/api/v1/accounts/" + accountId);

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(accountId.toString()))).andExpect(jsonPath("$.name", equalTo("Operating cash")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.ledgerBalance", equalTo("100")))
                .andExpect(jsonPath("$.clearedBalance", equalTo("100"))).andExpect(jsonPath("$.cashHeld", equalTo("100")))
                .andExpect(jsonPath("$.nativeCurrency", equalTo("USD"))).andExpect(jsonPath("$.projectionStatus", equalTo("CURRENT")));

        var corrected = mockMvc
                .perform(put("/api/v1/accounts/{accountId}/opening-state", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(openingCorrectionJson(uuid("10000000-0000-4000-8000-000000000002"), 1, "110", "Opening correction")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version", equalTo(2))).andReturn();

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("110")));

        var updated = mockMvc
                .perform(put("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(metadataJson(uuid("10000000-0000-4000-8000-000000000003"), correctedVersion(corrected), " Operating cash ", "Europe/London")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", equalTo("Operating cash"))).andExpect(jsonPath("$.timeZone", equalTo("Europe/London")))
                .andExpect(jsonPath("$.version", equalTo(3))).andReturn();

        var policy = mockMvc
                .perform(put("/api/v1/accounts/{accountId}/policy", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(policyJson(uuid("10000000-0000-4000-8000-000000000004"), 3, "SOFT_FLOOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.policy", equalTo("SOFT_FLOOR"))).andExpect(jsonPath("$.version", equalTo(4))).andReturn();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                archiveJson(uuid("10000000-0000-4000-8000-000000000005"), JsonPath.<Integer>read(policy.getResponse().getContentAsString(), "$.version"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived", equalTo(true))).andExpect(jsonPath("$.version", equalTo(5)));

        mockMvc.perform(get("/api/v1/accounts").param("includeArchived", "false").with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$.length()", equalTo(0))).andExpect(jsonPath("$.accounts").doesNotExist());
        mockMvc.perform(get("/api/v1/accounts").param("includeArchived", "true").with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].id", equalTo(accountId.toString())));
        assertNoSession(created);
    }

    @Test
    void holdingsOnlyBrokerageReturnsUntrackedCashAndCrossOwnerIdsStayNotFound() throws Exception {
        var owner = testIdentitySupport.create("account-http-holdings-owner@example.com");
        var other = testIdentitySupport.create("account-http-other-owner@example.com");
        var created = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(holdingsOnlyJson(uuid("20000000-0000-4000-8000-000000000001"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind", equalTo("BROKERAGE")))
                .andExpect(jsonPath("$.trackingMode", equalTo("HOLDINGS_ONLY"))).andExpect(jsonPath("$.cashCoverageStatus", equalTo("UNTRACKED"))).andReturn();
        var accountId = idFrom(created);

        var crossOwner = mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND"))).andReturn();
        HttpAssertions.assertProblem(crossOwner);
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
    }

    @Test
    void knownAccountNameConstraintUsesTheStableLedgerErrorCode() throws Exception {
        var owner = testIdentitySupport.create("account-http-constraint-owner@example.com");
        mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("21000000-0000-4000-8000-000000000001"), "Unique cash", "1"))).andExpect(status().isCreated());

        var duplicate = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(uuid("21000000-0000-4000-8000-000000000002"), " Unique cash ", "2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("ACCOUNT_NAME_CONFLICT"))).andReturn();
        HttpAssertions.assertProblem(duplicate);
        assertThat(duplicate.getResponse().getContentAsString()).doesNotContain("uix_ledger_financial_account_active_name");
    }

    @Test
    void accountListReturnsOwnerScopedDirectOrderedCollection() throws Exception {
        var owner = testIdentitySupport.create("account-http-list-owner@example.com");
        var other = testIdentitySupport.create("account-http-list-other-owner@example.com");
        var archivedAlpha = mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("22000000-0000-4000-8000-000000000001"), "Alpha cash", "1"))).andExpect(status().isCreated()).andReturn();
        var archivedAlphaId = idFrom(archivedAlpha);
        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", archivedAlphaId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(archiveJson(uuid("22000000-0000-4000-8000-000000000002"), 1))).andExpect(status().isOk());

        var activeAlpha = mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("22000000-0000-4000-8000-000000000003"), "alpha cash", "1"))).andExpect(status().isCreated()).andReturn();
        var activeAlphaId = idFrom(activeAlpha);
        var betaId = idFrom(mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("22000000-0000-4000-8000-000000000004"), "Beta cash", "1"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/accounts").with(other.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("22000000-0000-4000-8000-000000000005"), "Aardvark cash", "1"))).andExpect(status().isCreated());

        var active = mockMvc.perform(get("/api/v1/accounts").with(owner.asBearer())).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", equalTo(2))).andReturn();
        assertThat(JsonPath.<List<String>>read(active.getResponse().getContentAsString(), "$[*].id")).containsExactly(activeAlphaId.toString(),
                betaId.toString());

        var all = mockMvc.perform(get("/api/v1/accounts").param("includeArchived", "true").with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray()).andReturn();
        var allIds = JsonPath.<List<String>>read(all.getResponse().getContentAsString(), "$[*].id");
        assertThat(allIds.subList(0, 2)).containsExactlyElementsOf(List.of(archivedAlphaId.toString(), activeAlphaId.toString()).stream().sorted().toList());
        assertThat(allIds.get(2)).isEqualTo(betaId.toString());
        assertThat(all.getResponse().getContentAsString()).doesNotContain("\"accounts\"", "\"page\"", "\"size\"", "\"hasNext\"");
    }

    @Test
    void missingBearerAndInvalidOpeningContractAreRejectedWithoutLedgerWrites() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        var owner = testIdentitySupport.create("account-http-validation-owner@example.com");
        var result = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                        """
                                {"clientRequestId":"30000000-0000-4000-8000-000000000001","name":"Missing opening","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR"}
                                """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED"))).andReturn();
        HttpAssertions.assertProblem(result);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class)).isZero();

        var offsetZone = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                        """
                                {"clientRequestId":"30000000-0000-4000-8000-000000000002","name":"Offset zone","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"+02:00","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                                """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED"))).andReturn();
        HttpAssertions.assertProblem(offsetZone);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class)).isZero();

        var futureOpening = mockMvc
                .perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                        """
                                {"clientRequestId":"30000000-0000-4000-8000-000000000003","name":"Future opening","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-18T12:00:00Z"}}
                                """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("FUTURE_TIME_NOT_ALLOWED")))
                .andExpect(jsonPath("$.key", equalTo("error.ledger.future_time_not_allowed"))).andExpect(jsonPath("$.params").doesNotExist()).andReturn();
        HttpAssertions.assertProblem(futureOpening);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account", Integer.class)).isZero();
    }

    @Test
    void inactiveCurrencyAndUnknownCurrencyUseTheStableApplicationError() throws Exception {
        var owner = testIdentitySupport.create("account-http-currency-owner@example.com");
        setCurrencyActive("EUR", false);
        try {
            mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                    """
                            {"clientRequestId":"30500000-0000-4000-8000-000000000001","name":"Inactive currency","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"EUR","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                            """))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("ACCOUNT_CURRENCY_UNSUPPORTED")));
        } finally {
            setCurrencyActive("EUR", true);
        }

        mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"clientRequestId":"30500000-0000-4000-8000-000000000002","name":"Unknown currency","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"ZZZ","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"1","effectiveAt":"2026-08-17T11:00:00Z"}}
                        """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("ACCOUNT_CURRENCY_UNSUPPORTED")));
    }

    @Test
    void mutationVersionsAreRequiredAndNonNegativeAtTheHttpBoundary() throws Exception {
        var owner = testIdentitySupport.create("account-http-version-validation-owner@example.com");
        var account = mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(createJson(uuid("31000000-0000-4000-8000-000000000001"), "Versioned cash", "10"))).andExpect(status().isCreated()).andReturn();
        var accountId = idFrom(account);

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(metadataJson(uuid("31000000-0000-4000-8000-000000000007"), 0, "Stale version", "UTC"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_VERSION_CONFLICT")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"31000000-0000-4000-8000-000000000002","name":"Renamed","timeZone":"UTC"}
                """)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}/policy", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"31000000-0000-4000-8000-000000000003","policy":"HARD_FLOOR"}
                """)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"31000000-0000-4000-8000-000000000004"}
                """)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}/opening-state", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(
                """
                        {"clientRequestId":"31000000-0000-4000-8000-000000000005","amount":"11","effectiveAt":"2026-08-17T11:00:00Z","correctionReason":"Missing version"}
                        """))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(put("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientRequestId":"31000000-0000-4000-8000-000000000006","version":-1,"name":"Negative version","timeZone":"UTC"}
                """)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    private static String createJson(UUID requestId, String name, String amount) {
        return """
                {"clientRequestId":"%s","name":"%s","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"%s","effectiveAt":"2026-08-17T11:00:00Z"}}
                """
                .formatted(requestId, name, amount);
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
                .executeWithoutResult(status -> jdbcTemplate.update("UPDATE reference.currency SET active = ? WHERE code = ?", active, currency));
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

}
