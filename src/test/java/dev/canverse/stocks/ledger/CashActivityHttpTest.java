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
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
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
@Import(CashActivityHttpTest.TestOverrides.class)
@Execution(ExecutionMode.SAME_THREAD)
class CashActivityHttpTest {

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
    void activityRoutesRecordReplayListReverseAndReadTheProjection() throws Exception {
        var owner = authenticated("activity-http-owner@example.com");
        var accountId = createAccount(owner, uuid("10000000-0000-4000-8000-000000000001"), "Activity cash", "100");
        var activityRequestId = uuid("10000000-0000-4000-8000-000000000002");
        var effectiveAt = "2026-08-17T11:30:00Z";
        var recorded = mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(activityRequestId, "CASH_DEPOSIT", "25.00", effectiveAt, false)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.activityType", equalTo("CASH_DEPOSIT")))
                .andExpect(jsonPath("$.postings.length()", equalTo(1)))
                .andExpect(jsonPath("$.postings[0].amount", equalTo("25")))
                .andExpect(jsonPath("$.postings[0].role", equalTo("DEPOSIT")))
                .andReturn();
        var activityId = idFrom(recorded);
        assertThat(recorded.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/activities/" + activityId);
        assertTraceAndSession(recorded);

        var replay = mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(activityRequestId, "CASH_DEPOSIT", "25.0", effectiveAt, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(activityId.toString())))
                .andReturn();
        assertTraceAndSession(replay);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(activityRequestId, "CASH_DEPOSIT", "26", effectiveAt, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));

        mockMvc.perform(get("/api/v1/activities/{activityId}", activityId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(activityId.toString())))
                .andExpect(jsonPath("$.postings[0].currency", equalTo("USD")));

        var list = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(2)))
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(50)))
                .andExpect(jsonPath("$.hasNext", equalTo(false)))
                .andReturn();
        assertSliceShape(list);
        var activities =
                JsonPath.<List<Map<String, Object>>>read(list.getResponse().getContentAsString(), "$.items");
        assertThat(activities).anyMatch(activity -> activityId.toString().equals(activity.get("id")));

        var firstPage = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(1)))
                .andExpect(jsonPath("$.hasNext", equalTo(true)))
                .andReturn();
        assertSliceShape(firstPage);
        var firstPageId = JsonPath.<String>read(firstPage.getResponse().getContentAsString(), "$.items[0].id");
        var secondPage = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.page", equalTo(1)))
                .andExpect(jsonPath("$.size", equalTo(1)))
                .andExpect(jsonPath("$.hasNext", equalTo(false)))
                .andReturn();
        assertSliceShape(secondPage);
        var secondPageId = JsonPath.<String>read(secondPage.getResponse().getContentAsString(), "$.items[0].id");
        assertThat(secondPageId).isNotEqualTo(firstPageId);
        var emptyPage = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("page", "2")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(0)))
                .andExpect(jsonPath("$.page", equalTo(2)))
                .andExpect(jsonPath("$.size", equalTo(1)))
                .andExpect(jsonPath("$.hasNext", equalTo(false)))
                .andReturn();
        assertSliceShape(emptyPage);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("125")))
                .andExpect(jsonPath("$.cashHeld", equalTo("125")));

        var reversal = mockMvc.perform(post("/api/v1/activities/{activityId}/reversals", activityId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalJson(uuid("10000000-0000-4000-8000-000000000003"), "Duplicate deposit")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityType", equalTo("REVERSAL")))
                .andExpect(jsonPath("$.reversesActivityId", equalTo(activityId.toString())))
                .andExpect(jsonPath("$.postings[0].amount", equalTo("-25")))
                .andReturn();
        assertThat(reversal.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/activities/" + idFrom(reversal));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("100")));

        mockMvc.perform(post("/api/v1/activities/{activityId}/reversals", activityId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalJson(uuid("10000000-0000-4000-8000-000000000004"), "Second reversal")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACTIVITY_ALREADY_REVERSED")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/archive", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"10000000-0000-4000-8000-000000000006","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived", equalTo(true)));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(
                                uuid("10000000-0000-4000-8000-000000000005"), "CASH_DEPOSIT", "1", effectiveAt, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_ARCHIVED")));
    }

    @Test
    void activitySlicesHonorSortPolicyPageSizeBoundsAndSpringNormalization() throws Exception {
        var owner = authenticated("activity-http-pageable-owner@example.com");
        var accountId = createAccount(owner, uuid("11000000-0000-4000-8000-000000000001"), "Pageable cash", "100");
        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(
                                uuid("11000000-0000-4000-8000-000000000002"),
                                "CASH_DEPOSIT",
                                "5",
                                "2026-08-17T11:30:00Z",
                                false)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(
                                uuid("11000000-0000-4000-8000-000000000003"),
                                "CASH_DEPOSIT",
                                "7",
                                "2026-08-17T11:45:00Z",
                                false)))
                .andExpect(status().isCreated());

        var expectedEffectiveAscending = jdbcTemplate.queryForList(
                "SELECT a.id FROM ledger.activity a WHERE a.owner_user_account_id = ?"
                        + " AND EXISTS (SELECT 1 FROM ledger.money_posting p"
                        + " WHERE p.owner_user_account_id = a.owner_user_account_id"
                        + " AND p.activity_id = a.id AND p.financial_account_id = ?)"
                        + " ORDER BY a.effective_at ASC, a.id ASC",
                UUID.class,
                owner.userId(),
                accountId);
        var expectedEffectiveDescending = jdbcTemplate.queryForList(
                "SELECT a.id FROM ledger.activity a WHERE a.owner_user_account_id = ?"
                        + " AND EXISTS (SELECT 1 FROM ledger.money_posting p"
                        + " WHERE p.owner_user_account_id = a.owner_user_account_id"
                        + " AND p.activity_id = a.id AND p.financial_account_id = ?)"
                        + " ORDER BY a.effective_at DESC, a.id DESC",
                UUID.class,
                owner.userId(),
                accountId);
        var expectedRecordedDescending = jdbcTemplate.queryForList(
                "SELECT a.id FROM ledger.activity a WHERE a.owner_user_account_id = ?"
                        + " AND EXISTS (SELECT 1 FROM ledger.money_posting p"
                        + " WHERE p.owner_user_account_id = a.owner_user_account_id"
                        + " AND p.activity_id = a.id AND p.financial_account_id = ?)"
                        + " ORDER BY a.recorded_at DESC, a.id DESC",
                UUID.class,
                owner.userId(),
                accountId);

        var ascending = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("size", "2")
                        .param("sort", "effectiveAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(2)))
                .andExpect(jsonPath("$.items.length()", equalTo(2)))
                .andExpect(jsonPath("$.hasNext", equalTo(true)))
                .andReturn();
        assertThat(JsonPath.<List<String>>read(ascending.getResponse().getContentAsString(), "$.items[*].id"))
                .containsExactlyElementsOf(expectedEffectiveAscending.subList(0, 2).stream()
                        .map(UUID::toString)
                        .toList());

        var descending = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("size", "2")
                        .param("sort", "effectiveAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(2)))
                .andExpect(jsonPath("$.items.length()", equalTo(2)))
                .andReturn();
        assertThat(JsonPath.<List<String>>read(descending.getResponse().getContentAsString(), "$.items[*].id"))
                .containsExactlyElementsOf(expectedEffectiveDescending.subList(0, 2).stream()
                        .map(UUID::toString)
                        .toList());

        var recorded = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", accountId.toString())
                        .param("size", "101")
                        .param("sort", "recordedAt,desc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", equalTo(100)))
                .andExpect(jsonPath("$.items.length()", equalTo(3)))
                .andExpect(jsonPath("$.hasNext", equalTo(false)))
                .andReturn();
        assertThat(JsonPath.<List<String>>read(recorded.getResponse().getContentAsString(), "$.items[*].id"))
                .containsExactlyElementsOf(
                        expectedRecordedDescending.stream().map(UUID::toString).toList());

        mockMvc.perform(get("/api/v1/activities")
                        .param("page", "-2")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(50)));

        mockMvc.perform(get("/api/v1/activities")
                        .param("page", "not-a-page")
                        .param("size", "not-a-size")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(50)));

        var unsupported = mockMvc.perform(get("/api/v1/activities")
                        .param("sort", "activityType,desc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("sort")))
                .andReturn();
        assertProblemShape(unsupported);

        mockMvc.perform(get("/api/v1/activities")
                        .param("sort", "recordedAt,desc")
                        .param("sort", "effectiveAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("sort")));

        mockMvc.perform(get("/api/v1/activities")
                        .param("sort", "recordedAt,desc,ignorecase")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].field", equalTo("sort")));
    }

    @Test
    void activityListPreservesOwnerAccountPostingAndTieOrdering() throws Exception {
        var owner = authenticated("activity-http-list-owner@example.com");
        var otherOwner = authenticated("activity-http-list-other@example.com");
        var firstAccount =
                createAccount(owner, uuid("12000000-0000-4000-8000-000000000001"), "First activity account", "100");
        var secondAccount =
                createAccount(owner, uuid("12000000-0000-4000-8000-000000000002"), "Second activity account", "200");
        var otherAccount = createAccount(
                otherOwner, uuid("12000000-0000-4000-8000-000000000003"), "Other activity account", "300");
        var tiedEffectiveAt = "2026-08-17T11:30:00Z";
        var firstActivity =
                recordActivity(owner, firstAccount, uuid("12000000-0000-4000-8000-000000000004"), "5", tiedEffectiveAt);
        var secondActivity = recordActivity(
                owner, secondAccount, uuid("12000000-0000-4000-8000-000000000005"), "7", tiedEffectiveAt);
        var thirdActivity =
                recordActivity(owner, firstAccount, uuid("12000000-0000-4000-8000-000000000007"), "9", tiedEffectiveAt);
        var otherActivity = recordActivity(
                otherOwner, otherAccount, uuid("12000000-0000-4000-8000-000000000006"), "11", tiedEffectiveAt);

        var ownerList = mockMvc.perform(get("/api/v1/activities")
                        .param("sort", "recordedAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(5)))
                .andReturn();
        var ownerIds = JsonPath.<List<String>>read(ownerList.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(ownerIds).contains(firstActivity.toString(), secondActivity.toString());
        assertThat(ownerIds).doesNotContain(otherActivity.toString());
        var expectedRecordedAscending = jdbcTemplate.queryForList(
                "SELECT id FROM ledger.activity WHERE owner_user_account_id = ? ORDER BY recorded_at ASC, id ASC",
                UUID.class,
                owner.userId());
        assertThat(ownerIds)
                .containsExactlyElementsOf(
                        expectedRecordedAscending.stream().map(UUID::toString).toList());

        var firstAccountList = mockMvc.perform(get("/api/v1/activities")
                        .param("accountId", firstAccount.toString())
                        .param("sort", "effectiveAt,asc")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(3)))
                .andReturn();
        assertSliceShape(firstAccountList);

        var tiedExpected = jdbcTemplate.queryForList(
                "SELECT a.id FROM ledger.activity a"
                        + " WHERE a.owner_user_account_id = ?"
                        + " AND EXISTS (SELECT 1 FROM ledger.money_posting p"
                        + " WHERE p.owner_user_account_id = a.owner_user_account_id"
                        + " AND p.activity_id = a.id AND p.financial_account_id = ?)"
                        + " AND a.effective_at = ? ORDER BY a.effective_at ASC, a.id ASC",
                UUID.class,
                owner.userId(),
                firstAccount,
                java.time.OffsetDateTime.parse("2026-08-17T11:30:00Z"));
        var firstAccountIds =
                JsonPath.<List<String>>read(firstAccountList.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(firstAccountIds)
                .containsExactly(
                        JsonPath.<String>read(firstAccountList.getResponse().getContentAsString(), "$.items[0].id"),
                        tiedExpected.get(0).toString(),
                        tiedExpected.get(1).toString());
        assertThat(firstAccountIds).doesNotContain(secondActivity.toString(), otherActivity.toString());
        assertThat(firstAccountList.getResponse().getContentAsString())
                .contains(
                        firstActivity.toString(),
                        thirdActivity.toString(),
                        firstAccount.toString(),
                        "\"amount\":\"5\"");
    }

    private UUID recordActivity(Identity owner, UUID accountId, UUID requestId, String amount, String effectiveAt)
            throws Exception {
        return idFrom(mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(requestId, "CASH_DEPOSIT", amount, effectiveAt, false)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    void transferPreviewAndCommitExposeEqualOppositePostings() throws Exception {
        var owner = authenticated("transfer-http-owner@example.com");
        var sourceId = createAccount(owner, uuid("20000000-0000-4000-8000-000000000001"), "Transfer source", "100");
        var destinationId =
                createAccount(owner, uuid("20000000-0000-4000-8000-000000000002"), "Transfer destination", "10");
        var effectiveAt = "2026-08-17T11:45:00Z";

        mockMvc.perform(post("/api/v1/transfers/previews")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferPreviewJson(sourceId, destinationId, "25", effectiveAt)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.sourceBefore", equalTo("100")))
                .andExpect(jsonPath("$.sourceAfter", equalTo("75")))
                .andExpect(jsonPath("$.destinationBefore", equalTo("10")))
                .andExpect(jsonPath("$.destinationAfter", equalTo("35")))
                .andExpect(jsonPath("$.allowed", equalTo(true)));

        var transfer = mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(
                                uuid("20000000-0000-4000-8000-000000000003"),
                                sourceId,
                                destinationId,
                                "25",
                                effectiveAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postings.length()", equalTo(2)))
                .andExpect(jsonPath("$.postings[0].amount", equalTo("-25")))
                .andExpect(jsonPath("$.postings[1].amount", equalTo("25")))
                .andReturn();
        assertThat(transfer.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/activities/" + idFrom(transfer));

        mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"20000000-0000-4000-8000-000000000004","sourceAccountId":"%s","destinationAccountId":"%s","amount":"1","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-08-17T11:45:00Z","confirmPolicyBreach":false,"expectedSourceBalanceVersion":-1}
                                """.formatted(sourceId, destinationId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("75")));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", destinationId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("35")));
    }

    @Test
    void activityRoutesPreserveOwnerScopeAndControllerValidation() throws Exception {
        var owner = authenticated("activity-http-scope-owner@example.com");
        var other = authenticated("activity-http-scope-other@example.com");
        var accountId = createAccount(owner, uuid("30000000-0000-4000-8000-000000000001"), "Scoped cash", "100");
        var result = mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activityJson(
                                uuid("30000000-0000-4000-8000-000000000002"),
                                "CASH_DEPOSIT",
                                "not-an-amount",
                                "2026-08-17T11:30:00Z",
                                false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andReturn();
        assertProblemShape(result);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30000000-0000-4000-8000-000000000003","activityType":"CASH_DEPOSIT","amount":"1","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-08-17T11:30:00Z","confirmPolicyBreach":false,"expectedBalanceVersion":-1}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/activities", accountId)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"30000000-0000-4000-8000-000000000004","activityType":"NOT_A_TYPE","amount":"1","recordingMode":"CURRENT_ACTION","effectiveAt":"2026-08-17T11:30:00Z","confirmPolicyBreach":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("MALFORMED_REQUEST")));

        mockMvc.perform(get("/api/v1/activities/{activityId}", uuid("30000000-0000-4000-8000-000000000099"))
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACTIVITY_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/activities")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/transfers/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private UUID createAccount(Identity identity, UUID requestId, String name, String amount) throws Exception {
        var result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(requestId, name, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(result);
    }

    private Identity authenticated(String email) {
        var userId = registrationService.register(email, PASSWORD);
        var session = sessionIssuanceService.issue(userId, "ledger-activity-http-test");
        return new Identity(
                userId, tokenIssuanceService.issue(session.sessionId()).accessToken());
    }

    private static String accountJson(UUID requestId, String name, String amount) {
        return """
                {"clientRequestId":"%s","name":"%s","kind":"CASH_CURRENT","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"HARD_FLOOR","openingState":{"amount":"%s","effectiveAt":"2026-08-17T11:00:00Z"}}
                """.formatted(requestId, name, amount);
    }

    private static String activityJson(
            UUID requestId, String activityType, String amount, String effectiveAt, boolean confirmPolicyBreach) {
        return """
                {"clientRequestId":"%s","activityType":"%s","amount":"%s","recordingMode":"CURRENT_ACTION","effectiveAt":"%s","confirmPolicyBreach":%s}
                """.formatted(requestId, activityType, amount, effectiveAt, confirmPolicyBreach);
    }

    private static String reversalJson(UUID requestId, String reason) {
        return """
                {"clientRequestId":"%s","correctionReason":"%s"}
                """.formatted(requestId, reason);
    }

    private static String transferPreviewJson(UUID sourceId, UUID destinationId, String amount, String effectiveAt) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":"%s","recordingMode":"CURRENT_ACTION","effectiveAt":"%s","confirmPolicyBreach":false}
                """.formatted(sourceId, destinationId, amount, effectiveAt);
    }

    private static String transferJson(
            UUID requestId, UUID sourceId, UUID destinationId, String amount, String effectiveAt) {
        return """
                {"clientRequestId":"%s","sourceAccountId":"%s","destinationAccountId":"%s","amount":"%s","recordingMode":"CURRENT_ACTION","effectiveAt":"%s","confirmPolicyBreach":false}
                """.formatted(requestId, sourceId, destinationId, amount, effectiveAt);
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static void assertProblemShape(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body).containsKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp");
    }

    private static void assertSliceShape(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body).containsOnlyKeys("items", "page", "size", "hasNext");
    }

    private static void assertTraceAndSession(MvcResult result) {
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isNotBlank();
        assertThat(result.getRequest().getSession(false)).isNull();
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
