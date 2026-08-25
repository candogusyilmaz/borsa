package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
@Import(LedgerReconciliationHttpTest.TestOverrides.class)
@Execution(ExecutionMode.SAME_THREAD)
class LedgerReconciliationHttpTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T12:00:00Z");
    private static final String MAX_NUMERIC_38_18 = "99999999999999999999.999999999999999999";

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
                        "TRUNCATE TABLE ledger.reconciliation, ledger.money_posting, ledger.activity,"
                                + " ledger.account_balance_projection, ledger.account_cash_pocket, ledger.idempotency_record,"
                                + " ledger.financial_account, identity.device_session, identity.auth_identity,"
                                + " identity.user_account CASCADE"));
    }

    @Test
    void ownerCanPreviewCommitListDetailAndReadLastReconciliationMetadata() throws Exception {
        var owner = authenticated("reconciliation-http-owner@example.com");
        var accountId = createAccount(owner, "HTTP balanced", "100");
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReconciliation").doesNotExist())
                .andExpect(jsonPath("$.session").doesNotExist());
        mockMvc.perform(get("/api/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(uuid("10000000-0000-4000-8000-000000000002"), "25.50")))
                .andExpect(status().isCreated());

        var preview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("HTTP balanced statement", "125.50")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.ledgerOpeningBalance", equalTo("100")))
                .andExpect(jsonPath("$.periodNetPostedAmount", equalTo("25.5")))
                .andExpect(jsonPath("$.closingDifference", equalTo("0")))
                .andExpect(jsonPath("$.admissibleResolutions[0]", equalTo("CONFIRM_BALANCED")))
                .andReturn();
        var version = JsonPath.<Number>read(preview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();

        var committed = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("10000000-0000-4000-8000-000000000003"),
                                version,
                                "HTTP balanced statement",
                                "125.50",
                                "CONFIRM_BALANCED",
                                null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.resolution", equalTo("BALANCED")))
                .andExpect(jsonPath("$.lifecycleStatus", equalTo("CURRENT")))
                .andReturn();
        var reconciliationId = idFrom(committed);
        assertThat(committed.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/reconciliations/" + reconciliationId);

        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", reconciliationId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id", equalTo(reconciliationId.toString())));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.reconciliations.length()", equalTo(1)));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReconciliation.reconciliationId", equalTo(reconciliationId.toString())))
                .andExpect(jsonPath("$.lastReconciliation.statementClosingBalance", equalTo("125.5")));

        var otherOwner = authenticated("reconciliation-http-other@example.com");
        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", reconciliationId)
                        .header(HttpHeaders.AUTHORIZATION, otherOwner.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_NOT_FOUND")));
    }

    @Test
    void ownerCanCreateAdjustmentAndCorrectItThroughDedicatedRoute() throws Exception {
        var owner = authenticated("reconciliation-http-adjustment@example.com");
        var accountId = createAccount(owner, "HTTP adjusted", "100");
        var preview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("HTTP adjusted statement", "105")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissibleResolutions[0]", equalTo("CREATE_ADJUSTMENT")))
                .andReturn();
        var version = JsonPath.<Number>read(preview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        var commit = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("20000000-0000-4000-8000-000000000003"),
                                version,
                                "HTTP adjusted statement",
                                "105",
                                "CREATE_ADJUSTMENT",
                                "Unexplained difference")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resolution", equalTo("ADJUSTED")))
                .andExpect(jsonPath("$.adjustmentAmount", equalTo("5")))
                .andExpect(jsonPath("$.adjustmentReason", equalTo("Unexplained difference")))
                .andReturn();
        var originalId = idFrom(commit);

        var replacementPreview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("HTTP corrected statement", "104")))
                .andExpect(status().isOk())
                .andReturn();
        var replacementVersion = JsonPath.<Number>read(
                        replacementPreview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        var correction = mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", originalId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(
                                uuid("20000000-0000-4000-8000-000000000004"),
                                replacementVersion,
                                "HTTP corrected statement",
                                "104")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.supersedesReconciliationId", equalTo(originalId.toString())))
                .andExpect(jsonPath("$.closingDifference", equalTo("4")))
                .andReturn();
        var replacementId = idFrom(correction);

        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", originalId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus", equalTo("SUPERSEDED")));
        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", replacementId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus", equalTo("CURRENT")));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReconciliation.reconciliationId", equalTo(replacementId.toString())))
                .andExpect(jsonPath("$.lastReconciliation.statementClosingBalance", equalTo("104")));
    }

    @Test
    void unauthenticatedOrInvalidResolutionRequestsDoNotBecomePublic() throws Exception {
        var owner = authenticated("reconciliation-http-validation@example.com");
        var accountId = createAccount(owner, "HTTP validation", "100");
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("Unauthenticated", "100")))
                .andExpect(status().isUnauthorized());
        var invalid = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("Invalid period", "100")
                                .replace("2026-08-17T11:00:00Z", "2026-08-17T12:00:00Z")
                                .replace("2026-08-17T11:30:00Z", "2026-08-17T11:00:00Z")))
                .andExpect(status().isUnprocessableContent())
                .andReturn();
        assertThat(JsonPath.<Map<String, Object>>read(invalid.getResponse().getContentAsString(), "$")
                        .keySet())
                .contains("code", "traceId");
    }

    @Test
    void missingMalformedInvalidAndRevokedBearerCredentialsRemainUnauthorized() throws Exception {
        var owner = authenticated("reconciliation-http-authentication@example.com");
        var accountId = createAccount(owner, "Authentication", "100");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("authentication", "100")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("authentication", "100")))
                .andExpect(status().isUnauthorized());
        var invalidToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJpbnZhbGlkIn0.invalid-signature";
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("authentication", "100")))
                .andExpect(status().isUnauthorized());

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(
                        "UPDATE identity.device_session SET revoked_at = ?, revoke_reason = ? WHERE id = ?",
                        OBSERVED_AT.atOffset(ZoneOffset.UTC),
                        "reconciliation HTTP test",
                        owner.sessionId()));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("authentication", "100")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossOwnerPreviewCommitListDetailAndCorrectionAreIndistinguishableFromMissing() throws Exception {
        var owner = authenticated("reconciliation-http-scope-owner@example.com");
        var other = authenticated("reconciliation-http-scope-other@example.com");
        var accountId = createAccount(owner, "Scoped account", "100");
        var preview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("scoped", "100")))
                .andExpect(status().isOk())
                .andReturn();
        var version = JsonPath.<Number>read(preview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("scoped", "100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("40000000-0000-4000-8000-000000000001"),
                                version,
                                "scoped",
                                "100",
                                "CONFIRM_BALANCED",
                                null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));

        var committed = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("40000000-0000-4000-8000-000000000002"),
                                version,
                                "scoped",
                                "100",
                                "CONFIRM_BALANCED",
                                null)))
                .andExpect(status().isCreated())
                .andReturn();
        var reconciliationId = idFrom(committed);
        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", reconciliationId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", reconciliationId)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(
                                uuid("40000000-0000-4000-8000-000000000003"), version, "scoped correction", "100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_NOT_FOUND")));
    }

    @Test
    void requestValidationCoversMissingBoundsDecimalsUuidEnumsReasonsPeriodsAndOpeningContinuity() throws Exception {
        var owner = authenticated("reconciliation-http-request-validation@example.com");
        var accountId = createAccount(owner, "Request validation", "100");
        var valid = previewJson("valid", "100");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("\"statementReference\":\"valid\",", "")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("\"statementReference\":\"valid\"", "\"statementReference\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("", "100")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementOpeningAt\":\"2026-08-17T11:00:00Z\"", "\"statementOpeningAt\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementClosingAt\":\"2026-08-17T11:30:00Z\"", "\"statementClosingAt\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementOpeningBalance\":\"100.00\"", "\"statementOpeningBalance\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementClosingBalance\":\"100\"", "\"statementClosingBalance\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementOpeningAt\":\"2026-08-17T11:00:00Z\"",
                                "\"statementOpeningAt\":\"not-an-instant\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("r".repeat(201), "100")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("bad decimal", "not-a-decimal")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("2026-08-17T11:30:00Z", "2026-08-17T11:00:00Z")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("statementOpeningAt")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                        "\"statementOpeningAt\":\"2026-08-17T11:00:00Z\"",
                                        "\"statementOpeningAt\":\"2026-08-17T11:30:00Z\"")
                                .replace(
                                        "\"statementClosingAt\":\"2026-08-17T11:30:00Z\"",
                                        "\"statementClosingAt\":\"2026-08-17T11:00:00Z\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("statementOpeningAt")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("numeric maximum", MAX_NUMERIC_38_18, MAX_NUMERIC_38_18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementOpeningBalance", equalTo(MAX_NUMERIC_38_18)))
                .andExpect(jsonPath("$.statementClosingBalance", equalTo(MAX_NUMERIC_38_18)));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("numeric integer overflow", "100000000000000000000", "100")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("statementOpeningBalance")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("numeric scale overflow", "100", "0.0000000000000000001")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("statementClosingBalance")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("2026-08-17T11:30:00Z", "2026-08-18T11:30:00Z")))
                .andExpect(status().isUnprocessableContent());

        var preview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("difference", "105")))
                .andExpect(status().isOk())
                .andReturn();
        var version = JsonPath.<Number>read(preview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000001"),
                                -1,
                                "negative version",
                                "105",
                                "CREATE_ADJUSTMENT",
                                "reason")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        uuid("50000000-0000-4000-8000-000000000007"),
                                        version,
                                        "null client request",
                                        "105",
                                        "CREATE_ADJUSTMENT",
                                        "reason")
                                .replace(
                                        "\"clientRequestId\":\"50000000-0000-4000-8000-000000000007\"",
                                        "\"clientRequestId\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        uuid("50000000-0000-4000-8000-000000000008"),
                                        version,
                                        "null expected version",
                                        "105",
                                        "CREATE_ADJUSTMENT",
                                        "reason")
                                .replace(
                                        "\"expectedBalanceVersion\":%d".formatted(version),
                                        "\"expectedBalanceVersion\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        uuid("50000000-0000-4000-8000-000000000009"),
                                        version,
                                        "null resolution",
                                        "105",
                                        "CREATE_ADJUSTMENT",
                                        "reason")
                                .replace("\"resolution\":\"CREATE_ADJUSTMENT\"", "\"resolution\":null")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000002"),
                                version,
                                "wrong resolution",
                                "105",
                                "CONFIRM_BALANCED",
                                null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_RESOLUTION_REQUIRED")));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000003"),
                                version,
                                "missing reason",
                                "105",
                                "CREATE_ADJUSTMENT",
                                null)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000010"),
                                version,
                                "blank reason",
                                "105",
                                "CREATE_ADJUSTMENT",
                                "   ")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000011"),
                                version,
                                "oversized reason",
                                "105",
                                "CREATE_ADJUSTMENT",
                                "r".repeat(501))))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("50000000-0000-4000-8000-000000000004"),
                                version,
                                "balanced reason",
                                "100",
                                "CONFIRM_BALANCED",
                                "not allowed")))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        UUID.randomUUID(), version, "malformed uuid", "100", "CONFIRM_BALANCED", null)
                                .replace("\"clientRequestId\":\"", "\"clientRequestId\":\"not-a-uuid")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        uuid("50000000-0000-4000-8000-000000000005"),
                                        version,
                                        "malformed enum",
                                        "100",
                                        "CONFIRM_BALANCED",
                                        null)
                                .replace("CONFIRM_BALANCED", "NOT_A_RESOLUTION")))
                .andExpect(status().isBadRequest());

        var holdings = mockMvc.perform(
                        post("/api/v1/accounts")
                                .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"clientRequestId\":\"60000000-0000-4000-8000-000000000001\",\"name\":\"Holdings\",\"kind\":\"BROKERAGE\",\"trackingMode\":\"HOLDINGS_ONLY\",\"currency\":\"USD\",\"timeZone\":\"UTC\",\"policy\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        var holdingsId = idFrom(holdings);
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", holdingsId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("holdings", "0")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_ACTION_NOT_SUPPORTED")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace(
                                "\"statementOpeningBalance\":\"100.00\"", "\"statementOpeningBalance\":\"99\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]", equalTo("RECONCILIATION_OPENING_MISMATCH")));
        var openingMismatch = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                        uuid("50000000-0000-4000-8000-000000000012"),
                                        version,
                                        "commit opening mismatch",
                                        "105",
                                        "CREATE_ADJUSTMENT",
                                        "reason")
                                .replace("\"statementOpeningBalance\":\"100\"", "\"statementOpeningBalance\":\"99\"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_OPENING_MISMATCH")))
                .andReturn();
        assertThat(openingMismatch.getResponse().getContentAsString())
                .doesNotContain("commit opening mismatch")
                .doesNotContain("statementOpeningBalance");
        var correctionTarget = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", correctionTarget)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(
                                        uuid("50000000-0000-4000-8000-000000000013"),
                                        version,
                                        "blank correction reason",
                                        "100")
                                .replace(
                                        "\"correctionReason\":\"Corrected statement boundary\"",
                                        "\"correctionReason\":\"   \"")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", correctionTarget)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(
                                        uuid("50000000-0000-4000-8000-000000000014"),
                                        version,
                                        "oversized correction reason",
                                        "100")
                                .replace(
                                        "\"correctionReason\":\"Corrected statement boundary\"",
                                        "\"correctionReason\":\"" + "r".repeat(501) + "\"")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void idempotencyVersionAndSupersessionConflictsAreExposedAsSafeProblemDetails() throws Exception {
        var owner = authenticated("reconciliation-http-conflicts@example.com");
        var accountId = createAccount(owner, "HTTP conflicts", "100");
        var preview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("conflict", "100")))
                .andExpect(status().isOk())
                .andReturn();
        var version = JsonPath.<Number>read(preview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        var requestId = uuid("70000000-0000-4000-8000-000000000001");
        var balanced = commitJson(requestId, version, "conflict", "100", "CONFIRM_BALANCED", null);
        var committed = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanced))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanced))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(idFrom(committed).toString())));
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(requestId, version, "conflict", "101", "CREATE_ADJUSTMENT", "changed")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));

        var stalePreview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("stale version", "101")))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(uuid("70000000-0000-4000-8000-000000000002"), "1")))
                .andExpect(status().isCreated());
        var staleVersion = JsonPath.<Number>read(stalePreview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("70000000-0000-4000-8000-000000000003"),
                                staleVersion,
                                "stale version",
                                "102",
                                "CONFIRM_BALANCED",
                                null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("BALANCE_VERSION_CONFLICT")));

        var adjustedPreview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("supersession", "102")))
                .andExpect(status().isOk())
                .andReturn();
        var adjustedVersion = JsonPath.<Number>read(
                        adjustedPreview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        var adjusted = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(
                                uuid("70000000-0000-4000-8000-000000000004"),
                                adjustedVersion,
                                "supersession",
                                "102",
                                "CREATE_ADJUSTMENT",
                                "supersession difference")))
                .andExpect(status().isCreated())
                .andReturn();
        var originalId = idFrom(adjusted);
        var correctionPreview = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson("superseded replacement", "101")))
                .andExpect(status().isOk())
                .andReturn();
        var correctionVersion = JsonPath.<Number>read(
                        correctionPreview.getResponse().getContentAsString(), "$.projectionVersion")
                .longValue();
        var correction = correctionJson(
                uuid("70000000-0000-4000-8000-000000000005"), correctionVersion, "superseded replacement", "100");
        mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", originalId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reconciliations/{reconciliationId}/corrections", originalId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson(
                                uuid("70000000-0000-4000-8000-000000000006"),
                                correctionVersion,
                                "superseded again",
                                "100")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("RECONCILIATION_ALREADY_SUPERSEDED")));
    }

    @Test
    void reconciliationListUsesStableMultiRowCursorOrderingAndAccountFilterBinding() throws Exception {
        var owner = authenticated("reconciliation-http-cursor@example.com");
        var accountId = createAccount(owner, "Cursor account", "100");
        var otherAccountId = createAccount(owner, "Cursor other account", "100");
        var version = JsonPath.<Number>read(
                        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId)
                                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(previewJson("cursor-seed", "100")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.projectionVersion")
                .longValue();
        for (var index = 1; index <= 3; index++) {
            mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId)
                            .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(commitJson(
                                    uuid("80000000-0000-4000-8000-00000000000" + index),
                                    version,
                                    "cursor-" + index,
                                    "100",
                                    "CONFIRM_BALANCED",
                                    null)))
                    .andExpect(status().isCreated());
        }

        var first = mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliations.length()", equalTo(1)))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();
        var firstId = JsonPath.<String>read(first.getResponse().getContentAsString(), "$.reconciliations[0].id");
        var cursor = JsonPath.<String>read(first.getResponse().getContentAsString(), "$.nextCursor");
        var second = mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliations.length()", equalTo(1)))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();
        var secondId = JsonPath.<String>read(second.getResponse().getContentAsString(), "$.reconciliations[0].id");
        assertThat(secondId).isNotEqualTo(firstId);
        var thirdCursor = JsonPath.<String>read(second.getResponse().getContentAsString(), "$.nextCursor");
        var third = mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", accountId)
                        .param("limit", "1")
                        .param("cursor", thirdCursor)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliations.length()", equalTo(1)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn();
        var thirdId = JsonPath.<String>read(third.getResponse().getContentAsString(), "$.reconciliations[0].id");
        assertThat(thirdId).doesNotContain(firstId).doesNotContain(secondId);
        mockMvc.perform(get("/api/v1/accounts/{accountId}/reconciliations", otherAccountId)
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
    }

    private UUID createAccount(Identity owner, String name, String amount) throws Exception {
        var result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(UUID.randomUUID(), name, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(result);
    }

    private Identity authenticated(String email) {
        var userId = registrationService.register(email, PASSWORD);
        var session = sessionIssuanceService.issue(userId, "ledger-reconciliation-http-test");
        return new Identity(
                userId,
                session.sessionId(),
                tokenIssuanceService.issue(session.sessionId()).accessToken());
    }

    private static String accountJson(UUID requestId, String name, String amount) {
        return """
                {"clientRequestId":"%s","name":"%s","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"%s","effectiveAt":"2026-08-17T11:00:00Z"}}
                """.formatted(requestId, name, amount);
    }

    private static String activityJson(UUID requestId, String amount) {
        return """
                {"clientRequestId":"%s","activityType":"CASH_DEPOSIT","amount":"%s","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-08-17T11:15:00Z","confirmPolicyBreach":false}
                """.formatted(requestId, amount);
    }

    private static String previewJson(String reference, String closingBalance) {
        return previewJson(reference, "100.00", closingBalance);
    }

    private static String previewJson(String reference, String openingBalance, String closingBalance) {
        return """
                {"statementReference":"%s","statementOpeningAt":"2026-08-17T11:00:00Z","statementClosingAt":"2026-08-17T11:30:00Z","statementOpeningBalance":"%s","statementClosingBalance":"%s"}
                """.formatted(reference, openingBalance, closingBalance);
    }

    private static String commitJson(
            UUID requestId, long version, String reference, String closingBalance, String resolution, String reason) {
        var reasonJson = reason == null ? "null" : "\"%s\"".formatted(reason);
        return """
                {"statementReference":"%s","statementOpeningAt":"2026-08-17T11:00:00Z","statementClosingAt":"2026-08-17T11:30:00Z","statementOpeningBalance":"100","statementClosingBalance":"%s","clientRequestId":"%s","expectedBalanceVersion":%d,"resolution":"%s","adjustmentReason":%s}
                """.formatted(reference, closingBalance, requestId, version, resolution, reasonJson);
    }

    private static String correctionJson(UUID requestId, long version, String reference, String closingBalance) {
        return """
                {"statementReference":"%s","statementOpeningAt":"2026-08-17T11:00:00Z","statementClosingAt":"2026-08-17T11:30:00Z","statementOpeningBalance":"100","statementClosingBalance":"%s","clientRequestId":"%s","expectedBalanceVersion":%d,"resolution":"CREATE_ADJUSTMENT","adjustmentReason":"Corrected difference","correctionReason":"Corrected statement boundary"}
                """.formatted(reference, closingBalance, requestId, version);
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record Identity(UUID userId, UUID sessionId, String token) {
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
