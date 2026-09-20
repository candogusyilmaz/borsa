package dev.canverse.stocks.investing;

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
import dev.canverse.stocks.testing.TestIdentitySupport.Identity;
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
class InvestingTradeHttpTest {

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    TestIdentitySupport testIdentitySupport;
    @Autowired
    TestClock testClock;
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-19T12:00:00Z");
    private static final String OPENED_AT = "2026-09-19T10:00:00Z";
    private static final String FUNDED_AT = "2026-09-19T10:30:00Z";
    private static final String BUY_AT = "2026-09-19T11:00:00Z";
    private static final String PARTIAL_SELL_AT = "2026-09-19T11:02:00Z";
    private static final String FULL_SELL_AT = "2026-09-19T11:20:00Z";
    private static final String REOPEN_AT = "2026-09-19T11:30:00Z";
    private static final UUID MANUAL_MARKET_ID = uuid("10000000-0000-0000-0000-000000000002");
    private static final UUID GLOBAL_INSTRUMENT_ID = uuid("60000000-0000-4000-8000-000000000001");
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanOwnerDataAndCreateGlobalFixture() {
        testClock.setInstant(OBSERVED_AT);

        databaseCleaner.resetApplicationState();
        ensureGlobalInstrument();
    }

    @Test
    void buySellCloseReopenAndReversalExposeCashSecurityAndPositionFacts() throws Exception {
        var owner = testIdentitySupport.create("investing-lifecycle-owner@example.com");
        var ownerInstrument = createInstrument(owner, "OWNER-EQUITY", "EQUITY", "USD");
        var accounts = fundedBrokerage(owner, "Lifecycle", "500");

        mockMvc.perform(post("/api/v1/trades/previews").contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(accounts.brokerageId(), GLOBAL_INSTRUMENT_ID, "BUY", "1", "20", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(accounts.brokerageId(), GLOBAL_INSTRUMENT_ID, "BUY", "1", "20", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instrumentId", equalTo(GLOBAL_INSTRUMENT_ID.toString()))).andExpect(jsonPath("$.grossAmount", equalTo("20")));

        var buyPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accounts.brokerageId(), ownerInstrument, "BUY", "10", "10", "2", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grossAmount", equalTo("100"))).andExpect(jsonPath("$.cashDelta", equalTo("-102")))
                .andExpect(jsonPath("$.cashBalanceBefore", equalTo("500"))).andExpect(jsonPath("$.cashBalanceAfter", equalTo("398")))
                .andExpect(jsonPath("$.allowed", equalTo(true))).andExpect(jsonPath("$.quantityBefore", equalTo("0")))
                .andExpect(jsonPath("$.quantityAfter", equalTo("10"))).andExpect(jsonPath("$.remainingBasisAfter", equalTo("102")))
                .andExpect(jsonPath("$.positionVersion", equalTo(0))).andReturn();
        var buyCashVersion = longJson(buyPreview, "$.cashBalanceVersion");
        var buyPositionVersion = longJson(buyPreview, "$.positionVersion");
        var buyRequestId = uuid("61000000-0000-4000-8000-000000000001");
        var buyJson = commitJson(buyRequestId, accounts.brokerageId(), ownerInstrument, "BUY", "10", "10", "2", "CURRENT_ACTION", BUY_AT, 0, false,
                buyCashVersion, buyPositionVersion);
        var buyResult = mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(buyJson))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.side", equalTo("BUY")))
                .andExpect(jsonPath("$.grossAmount", equalTo("100"))).andExpect(jsonPath("$.commissionAmount", equalTo("2")))
                .andExpect(jsonPath("$.cashDelta", equalTo("-102"))).andExpect(jsonPath("$.securityPosting.role", equalTo("BUY")))
                .andExpect(jsonPath("$.cashPostings.length()", equalTo(2))).andExpect(jsonPath("$.cashPostings[0].role", equalTo("TRADE_PURCHASE")))
                .andExpect(jsonPath("$.cashPostings[0].amount", equalTo("-100"))).andExpect(jsonPath("$.cashPostings[1].role", equalTo("FEE")))
                .andExpect(jsonPath("$.cashPostings[1].amount", equalTo("-2"))).andReturn();
        var buyId = idFrom(buyResult);
        assertThat(buyResult.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/trades/" + buyId);
        assertTraceAndSession(buyResult);

        var reconciliationId = reconcile(owner, accounts.brokerageId(), 398);

        var idempotentReplay = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson.replace("\"quantity\":\"10\"", "\"quantity\":\"10.00\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id", equalTo(buyId.toString()))).andReturn();
        assertTraceAndSession(idempotentReplay);
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(buyJson.replace("\"commissionAmount\":\"2\"", "\"commissionAmount\":\"3\""))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));

        mockMvc.perform(get("/api/v1/trades/{tradeId}", buyId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.securityPosting.quantityDelta", equalTo("10")));
        mockMvc.perform(get("/api/v1/activities/{activityId}", buyId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.securityPostings.length()", equalTo(1))).andExpect(jsonPath("$.securityPostings[0].role", equalTo("BUY")));

        var positionAfterBuy = mockMvc
                .perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", accounts.brokerageId(), ownerInstrument).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity", equalTo("10"))).andExpect(jsonPath("$.remainingEconomicBasis", equalTo("102")))
                .andExpect(jsonPath("$.calculationPolicy", equalTo("WEIGHTED_AVERAGE_ECONOMIC_V1")))
                .andExpect(jsonPath("$.projectionStatus", equalTo("CURRENT"))).andReturn();
        assertThat(longJson(positionAfterBuy, "$.version")).isZero();

        var partialPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accounts.brokerageId(), ownerInstrument, "SELL", "4", "15", "1", "CURRENT_ACTION", PARTIAL_SELL_AT, 1, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cashDelta", equalTo("59"))).andExpect(jsonPath("$.allocatedBasis", equalTo("40.8")))
                .andExpect(jsonPath("$.realizedEconomicPnl", equalTo("18.2"))).andReturn();
        var partialRequestId = uuid("61000000-0000-4000-8000-000000000002");
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(partialRequestId, accounts.brokerageId(), ownerInstrument, "SELL", "4", "15", "1", "CURRENT_ACTION", PARTIAL_SELL_AT, 1,
                        false, longJson(partialPreview, "$.cashBalanceVersion"), longJson(partialPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.side", equalTo("SELL"))).andExpect(jsonPath("$.cashDelta", equalTo("59")))
                .andExpect(jsonPath("$.cashPostings[0].role", equalTo("TRADE_PROCEEDS"))).andExpect(jsonPath("$.cashPostings[0].amount", equalTo("60")))
                .andReturn();
        mockMvc.perform(get("/api/v1/reconciliations/{reconciliationId}", reconciliationId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus", equalTo("STALE")));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accounts.brokerageId()).param("asOf", "2026-09-19T11:05:00Z").with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ledgerBalance", equalTo("457")));

        var fullPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accounts.brokerageId(), ownerInstrument, "SELL", "6", "15", "0", "CURRENT_ACTION", FULL_SELL_AT, 2, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityAfter", equalTo("0"))).andExpect(jsonPath("$.remainingBasisAfter", equalTo("0")))
                .andExpect(jsonPath("$.allocatedBasis", equalTo("61.2"))).andExpect(jsonPath("$.realizedEconomicPnl", equalTo("28.8"))).andReturn();
        var fullRequestId = uuid("61000000-0000-4000-8000-000000000003");
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(fullRequestId, accounts.brokerageId(), ownerInstrument, "SELL", "6", "15", "0", "CURRENT_ACTION", FULL_SELL_AT, 2, false,
                        longJson(fullPreview, "$.cashBalanceVersion"), longJson(fullPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cashPostings.length()", equalTo(1)))
                .andExpect(jsonPath("$.cashPostings[0].role", equalTo("TRADE_PROCEEDS")));

        mockMvc.perform(get("/api/v1/investing/positions").with(owner.asBearer()).param("accountId", accounts.brokerageId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(0)));
        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", accounts.brokerageId(), ownerInstrument).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity", equalTo("0"))).andExpect(jsonPath("$.remainingEconomicBasis", equalTo("0")));

        var reopenPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accounts.brokerageId(), ownerInstrument, "BUY", "2", "10", "1", "CURRENT_ACTION", REOPEN_AT, 3, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityAfter", equalTo("2"))).andExpect(jsonPath("$.remainingBasisAfter", equalTo("21")))
                .andReturn();
        var reopenRequestId = uuid("61000000-0000-4000-8000-000000000004");
        var reopenResult = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(reopenRequestId, accounts.brokerageId(), ownerInstrument, "BUY", "2", "10", "1", "CURRENT_ACTION", REOPEN_AT, 3,
                                false, longJson(reopenPreview, "$.cashBalanceVersion"), longJson(reopenPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andReturn();
        var reopenId = idFrom(reopenResult);
        var originalSecurityPostingId = jdbcTemplate.queryForObject("SELECT id FROM ledger.security_posting WHERE activity_id = ?", UUID.class, reopenId);
        var reversal = mockMvc
                .perform(post("/api/v1/activities/{activityId}/reversals", reopenId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(reversalJson(uuid("61000000-0000-4000-8000-000000000005"), "Incorrect reopen")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.policyDecision", equalTo("NOT_APPLICABLE")))
                .andExpect(jsonPath("$.reversesActivityId", equalTo(reopenId.toString()))).andExpect(jsonPath("$.postings.length()", equalTo(2)))
                .andExpect(jsonPath("$.securityPostings.length()", equalTo(1))).andExpect(jsonPath("$.securityPostings[0].role", equalTo("REVERSAL")))
                .andReturn();
        var reversalId = idFrom(reversal);
        assertThat(
                jdbcTemplate.queryForObject("SELECT reverses_security_posting_id FROM ledger.security_posting WHERE activity_id = ?", UUID.class, reversalId))
                .isEqualTo(originalSecurityPostingId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE activity_id = ?" + " AND reverses_money_posting_id IS NOT NULL",
                Integer.class, reversalId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/trades/{tradeId}", reopenId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalActivityId", equalTo(reversalId.toString())))
                .andExpect(jsonPath("$.reversalReason", equalTo("Incorrect reopen")));
        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", accounts.brokerageId(), ownerInstrument).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity", equalTo("0"))).andExpect(jsonPath("$.remainingEconomicBasis", equalTo("0")));
        mockMvc.perform(post("/api/v1/activities/{activityId}/reversals", reopenId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(uuid("61000000-0000-4000-8000-000000000006"), "Second reversal"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ACTIVITY_ALREADY_REVERSED")));

        var otherOwner = testIdentitySupport.create("investing-lifecycle-other@example.com");
        mockMvc.perform(get("/api/v1/trades/{tradeId}", buyId).with(otherOwner.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("TRADE_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", accounts.brokerageId(), ownerInstrument).with(otherOwner.asBearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("POSITION_NOT_FOUND")));

        var history = mockMvc
                .perform(get("/api/v1/trades").with(owner.asBearer()).param("accountId", accounts.brokerageId().toString()).param("instrumentId",
                        ownerInstrument.toString()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items.length()", equalTo(4))).andExpect(jsonPath("$.size", equalTo(50))).andReturn();
        HttpAssertions.assertSlice(history);
        var activityList = mockMvc.perform(get("/api/v1/activities").param("accountId", accounts.brokerageId().toString()).with(owner.asBearer()))
                .andExpect(status().isOk()).andReturn();
        var activityItems = JsonPath.<List<Map<String, Object>>>read(activityList.getResponse().getContentAsString(), "$.items");
        assertThat(activityItems)
                .anyMatch(item -> buyId.toString().equals(item.get("id")) && item.get("securityPostings") instanceof List<?> postings && postings.size() == 1);
        assertThat(activityItems).anyMatch(
                item -> "OPENING_BALANCE".equals(item.get("activityType")) && item.get("securityPostings") instanceof List<?> postings && postings.isEmpty());
    }

    @Test
    void fundedEtfTradePreviewCommitAndReadsExposeCashSecurityAndPositionEffects() throws Exception {
        var owner = testIdentitySupport.create("investing-etf-trade-owner@example.com");
        var instrumentId = createInstrument(owner, "FUNDED-ETF", "ETF", "USD");
        var accounts = fundedBrokerage(owner, "ETF", "500");

        var preview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accounts.brokerageId(), instrumentId, "BUY", "3", "12.34", "1", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instrumentId", equalTo(instrumentId.toString())))
                .andExpect(jsonPath("$.grossAmount", equalTo("37.02"))).andExpect(jsonPath("$.cashDelta", equalTo("-38.02")))
                .andExpect(jsonPath("$.cashBalanceBefore", equalTo("500"))).andExpect(jsonPath("$.cashBalanceAfter", equalTo("461.98")))
                .andExpect(jsonPath("$.quantityAfter", equalTo("3"))).andExpect(jsonPath("$.remainingBasisAfter", equalTo("38.02"))).andReturn();

        var committed = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("61500000-0000-4000-8000-000000000001"), accounts.brokerageId(), instrumentId, "BUY", "3", "12.34", "1",
                                "CURRENT_ACTION", BUY_AT, 0, false, longJson(preview, "$.cashBalanceVersion"), longJson(preview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instrumentType", equalTo("ETF"))).andExpect(jsonPath("$.securityPosting.role", equalTo("BUY")))
                .andExpect(jsonPath("$.securityPosting.quantityDelta", equalTo("3"))).andReturn();
        var tradeId = idFrom(committed);

        var tradeRead = mockMvc.perform(get("/api/v1/trades/{tradeId}", tradeId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentType", equalTo("ETF"))).andExpect(jsonPath("$.instrumentId", equalTo(instrumentId.toString())))
                .andExpect(jsonPath("$.grossAmount", equalTo("37.02"))).andExpect(jsonPath("$.commissionAmount", equalTo("1")))
                .andExpect(jsonPath("$.cashDelta", equalTo("-38.02"))).andExpect(jsonPath("$.securityPosting.instrumentId", equalTo(instrumentId.toString())))
                .andExpect(jsonPath("$.securityPosting.quantityDelta", equalTo("3"))).andExpect(jsonPath("$.cashPostings.length()", equalTo(2))).andReturn();
        var readBody = tradeRead.getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(readBody, "$.cashPostings[*].role")).containsExactlyInAnyOrder("TRADE_PURCHASE", "FEE");
        assertThat(JsonPath.<List<String>>read(readBody, "$.cashPostings[*].amount")).containsExactlyInAnyOrder("-37.02", "-1");

        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", accounts.brokerageId(), instrumentId).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instrumentType", equalTo("ETF"))).andExpect(jsonPath("$.quantity", equalTo("3")))
                .andExpect(jsonPath("$.remainingEconomicBasis", equalTo("38.02")))
                .andExpect(jsonPath("$.calculationPolicy", equalTo("WEIGHTED_AVERAGE_ECONOMIC_V1")))
                .andExpect(jsonPath("$.projectionStatus", equalTo("CURRENT")));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accounts.brokerageId()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance", equalTo("461.98")));
    }

    @Test
    void historicalNegativeCashAndInactiveInstrumentCloseFollowRecordingRules() throws Exception {
        var owner = testIdentitySupport.create("investing-historical-owner@example.com");
        var instrumentId = createInstrument(owner, "HISTORICAL-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("62000000-0000-4000-8000-000000000001"), "Historical brokerage", "BROKERAGE", "0", "HARD_FLOOR");

        var blockedPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "50", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed", equalTo(false))).andExpect(jsonPath("$.cashBalanceAfter", equalTo("-50")))
                .andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("62000000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "1", "50", "0", "CURRENT_ACTION", BUY_AT, 0,
                        false, longJson(blockedPreview, "$.cashBalanceVersion"), longJson(blockedPreview, "$.positionVersion"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_FUNDS")));

        var historicalPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "50", "0", "HISTORICAL_FACT", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed", equalTo(true)))
                .andExpect(jsonPath("$.policyDecision", equalTo("HISTORICAL_BREACH_RECORDED"))).andReturn();
        var historicalBuy = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("62000000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "BUY", "1", "50", "0", "HISTORICAL_FACT",
                                BUY_AT, 0, false, longJson(historicalPreview, "$.cashBalanceVersion"), longJson(historicalPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.policyDecision", equalTo("HISTORICAL_BREACH_RECORDED"))).andReturn();
        var buyId = idFrom(historicalBuy);

        var updatedInstrument = mockMvc.perform(
                put("/api/v1/reference/instruments/{instrumentId}", instrumentId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":0,"name":"Historical equity inactive","valuationMethod":"NOT_VALUED","active":false,"aliases":[]}
                        """)).andExpect(status().isOk()).andExpect(jsonPath("$.active", equalTo(false))).andReturn();

        var inactiveBuyPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "50", "0", "CURRENT_ACTION", PARTIAL_SELL_AT, 1, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("UNSUPPORTED_INSTRUMENT")));
        assertThat(updatedInstrument).isNotNull();
        assertThat(inactiveBuyPreview).isNotNull();

        var sellPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "SELL", "1", "50", "0", "CURRENT_ACTION", PARTIAL_SELL_AT, 1, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cashBalanceAfter", equalTo("0"))).andExpect(jsonPath("$.quantityAfter", equalTo("0")))
                .andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("62000000-0000-4000-8000-000000000004"), brokerageId, instrumentId, "SELL", "1", "50", "0", "CURRENT_ACTION",
                        PARTIAL_SELL_AT, 1, false, longJson(sellPreview, "$.cashBalanceVersion"), longJson(sellPreview, "$.positionVersion"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/activities/{activityId}", buyId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.securityPostings.length()", equalTo(1)));

        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "BUY", "1", "50", "0", "HISTORICAL_FACT", FULL_SELL_AT, 2, false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", equalTo(true))).andExpect(jsonPath("$.policyDecision", equalTo("HISTORICAL_BREACH_RECORDED")));
    }

    @Test
    void ownerInstrumentVisibilityCurrencyAndUnsupportedTypeAreEnforced() throws Exception {
        var owner = testIdentitySupport.create("investing-visibility-owner@example.com");
        var otherOwner = testIdentitySupport.create("investing-visibility-other@example.com");
        var brokerageId = createAccount(owner, uuid("63000000-0000-4000-8000-000000000001"), "Visibility brokerage", "BROKERAGE", "100", "HARD_FLOOR");
        var otherInstrument = createInstrument(otherOwner, "OTHER-OWNER-EQUITY", "EQUITY", "USD");
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, otherInstrument, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("UNSUPPORTED_INSTRUMENT")));

        var gbpInstrument = createInstrument(owner, "GBP-EQUITY", "EQUITY", "GBP");
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, gbpInstrument, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("TRADE_CURRENCY_MISMATCH")));

        var fundInstrument = createInstrument(owner, "UNSUPPORTED-FUND", "FUND", "USD");
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, fundInstrument, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("UNSUPPORTED_INSTRUMENT")));
    }

    @Test
    void currentBuyUsesSoftFloorConfirmationBeforeRecordingNegativeCash() throws Exception {
        var owner = testIdentitySupport.create("investing-soft-floor-owner@example.com");
        var instrumentId = createInstrument(owner, "SOFT-FLOOR-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("64000000-0000-4000-8000-000000000001"), "Soft floor brokerage", "BROKERAGE", "0", "SOFT_FLOOR");

        var unconfirmedPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "1", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed", equalTo(false))).andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("64000000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "1", "1", "0", "CURRENT_ACTION", BUY_AT, 0,
                        false, longJson(unconfirmedPreview, "$.cashBalanceVersion"), longJson(unconfirmedPreview, "$.positionVersion"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("POLICY_BREACH_CONFIRMATION_REQUIRED")));

        var confirmedPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "1", "0", "CURRENT_ACTION", BUY_AT, 0, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed", equalTo(true))).andExpect(jsonPath("$.policyDecision", equalTo("CONFIRMED_BREACH")))
                .andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("64000000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "BUY", "1", "1", "0", "CURRENT_ACTION", BUY_AT, 0,
                        true, longJson(confirmedPreview, "$.cashBalanceVersion"), longJson(confirmedPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.policyDecision", equalTo("CONFIRMED_BREACH")))
                .andExpect(jsonPath("$.cashDelta", equalTo("-1")));
    }

    @Test
    void halfEvenMidpointGrossSurvivesPostgresPersistenceAndTradeReads() throws Exception {
        var owner = testIdentitySupport.create("investing-half-even-owner@example.com");
        var instrumentId = createInstrument(owner, "HALF-EVEN-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("64500000-0000-4000-8000-000000000001"), "Half even brokerage", "BROKERAGE", "100", "HARD_FLOOR");
        var effectiveAt = "2026-09-19T11:00:01Z";

        var preview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "1.025", "0", "CURRENT_ACTION", effectiveAt, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grossAmount", equalTo("1.02"))).andExpect(jsonPath("$.cashDelta", equalTo("-1.02")))
                .andExpect(jsonPath("$.remainingBasisAfter", equalTo("1.02"))).andReturn();
        var committed = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("64500000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "1", "1.025", "0", "CURRENT_ACTION",
                                effectiveAt, 0, false, longJson(preview, "$.cashBalanceVersion"), longJson(preview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.grossAmount", equalTo("1.02"))).andExpect(jsonPath("$.cashDelta", equalTo("-1.02")))
                .andReturn();

        mockMvc.perform(get("/api/v1/trades/{activityId}", idFrom(committed)).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount", equalTo("1.02"))).andExpect(jsonPath("$.cashDelta", equalTo("-1.02")));
    }

    @Test
    void backdatedAndSameTimeTradesReplayByExplicitOrderAndReadSlicesStayDeterministic() throws Exception {
        var owner = testIdentitySupport.create("investing-backdated-owner@example.com");
        var instrumentId = createInstrument(owner, "PAGE-A-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("64600000-0000-4000-8000-000000000001"), "Paged brokerage", "BROKERAGE", "500", "HARD_FLOOR");

        var laterBuy = commitTrade(owner, uuid("64600000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "2", "10", "0",
                "2026-09-19T11:02:00Z", 0);
        var backdatedBuy = commitTrade(owner, uuid("64600000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "BUY", "1", "5", "0",
                "2026-09-19T11:00:00Z", 0);
        var sameTimeSale = commitTrade(owner, uuid("64600000-0000-4000-8000-000000000004"), brokerageId, instrumentId, "SELL", "1", "12", "0",
                "2026-09-19T11:00:00Z", 1);
        commitTrade(owner, uuid("64600000-0000-4000-8000-000000000005"), brokerageId, GLOBAL_INSTRUMENT_ID, "BUY", "1", "15", "0", "2026-09-19T11:01:00Z", 0);

        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", brokerageId, instrumentId).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity", equalTo("2"))).andExpect(jsonPath("$.remainingEconomicBasis", equalTo("20")))
                .andExpect(jsonPath("$.cumulativeRealizedEconomicPnl", equalTo("7")));

        var newestTrades = mockMvc
                .perform(get("/api/v1/trades").with(owner.asBearer()).param("accountId", brokerageId.toString()).param("instrumentId", instrumentId.toString())
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(2))).andExpect(jsonPath("$.hasNext", equalTo(true)))
                .andExpect(jsonPath("$.items[0].id", equalTo(idFrom(laterBuy).toString())))
                .andExpect(jsonPath("$.items[1].id", equalTo(idFrom(sameTimeSale).toString()))).andExpect(jsonPath("$.items[1].economicSequence", equalTo(1)))
                .andReturn();
        HttpAssertions.assertSlice(newestTrades);
        var olderTrades = mockMvc
                .perform(get("/api/v1/trades").with(owner.asBearer()).param("accountId", brokerageId.toString()).param("instrumentId", instrumentId.toString())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(idFrom(backdatedBuy).toString()))).andExpect(jsonPath("$.hasNext", equalTo(false))).andReturn();
        HttpAssertions.assertSlice(olderTrades);
        mockMvc.perform(get("/api/v1/trades").with(owner.asBearer()).param("instrumentId", GLOBAL_INSTRUMENT_ID.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(1))).andExpect(jsonPath("$.items[0].instrumentId", equalTo(GLOBAL_INSTRUMENT_ID.toString())));

        var openPositions = mockMvc
                .perform(get("/api/v1/investing/positions").with(owner.asBearer()).param("accountId", brokerageId.toString()).param("page", "0").param("size",
                        "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(1))).andExpect(jsonPath("$.hasNext", equalTo(true))).andReturn();
        HttpAssertions.assertSlice(openPositions);
        mockMvc.perform(get("/api/v1/investing/positions").with(owner.asBearer()).param("accountId", brokerageId.toString())
                .param("sort", "instrumentSymbol,desc").param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].instrumentSymbol", equalTo("PR-GLOBAL-EQUITY"))).andExpect(jsonPath("$.items[0].quantity", equalTo("1")));

        var otherOwner = testIdentitySupport.create("investing-backdated-other@example.com");
        mockMvc.perform(get("/api/v1/trades").with(otherOwner.asBearer())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(0)));
        mockMvc.perform(get("/api/v1/investing/positions").with(otherOwner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(0)));
    }

    @Test
    void precisionFutureEconomicOrderOversellAndStaleVersionsReturnStableProblems() throws Exception {
        var owner = testIdentitySupport.create("investing-trade-validation-owner@example.com");
        var instrumentId = createInstrument(owner, "VALIDATION-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("65000000-0000-4000-8000-000000000001"), "Validation brokerage", "BROKERAGE", "100", "HARD_FLOOR");

        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", "2026-09-19T12:00:01Z", 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("FUTURE_TIME_NOT_ALLOWED")));
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "BUY", "1", "10", "0.001", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INVALID_SETTLED_PRECISION")));

        var firstPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "2", "10", "1", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cashBalanceAfter", equalTo("79"))).andExpect(jsonPath("$.remainingBasisAfter", equalTo("21")))
                .andReturn();
        mockMvc.perform(
                post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("65000000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "2", "10", "1", "CURRENT_ACTION",
                                BUY_AT, 0, false, longJson(firstPreview, "$.cashBalanceVersion"), longJson(firstPreview, "$.positionVersion"))))
                .andExpect(status().isCreated());

        var secondAt = "2026-09-19T11:00:01Z";
        var secondPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", secondAt, 0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityAfter", equalTo("3"))).andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("65000000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", secondAt,
                        0, false, longJson(secondPreview, "$.cashBalanceVersion"), longJson(secondPreview, "$.positionVersion"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_ECONOMIC_ORDER")));
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "SELL", "4", "10", "0", "CURRENT_ACTION", "2026-09-19T11:00:02Z", 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_POSITION_QUANTITY")));
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "SELL", "1", "10", "10", "CURRENT_ACTION", "2026-09-19T11:00:02Z", 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("TRADE_PROCEEDS_NOT_POSITIVE")));

        var staleCashPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", "2026-09-19T11:00:02Z", 0, false)))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("65000000-0000-4000-8000-000000000004"), brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION",
                        "2026-09-19T11:00:02Z", 0, false, longJson(firstPreview, "$.cashBalanceVersion"), longJson(staleCashPreview, "$.positionVersion"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("BALANCE_VERSION_CONFLICT")));
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("65000000-0000-4000-8000-000000000005"), brokerageId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION",
                        "2026-09-19T11:00:02Z", 0, false, longJson(staleCashPreview, "$.cashBalanceVersion"), longJson(firstPreview, "$.positionVersion"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("POSITION_VERSION_CONFLICT")));

        var cashAccountId = createAccount(owner, uuid("65000000-0000-4000-8000-000000000006"), "Not brokerage", "CASH_CURRENT", "100", "HARD_FLOOR");
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(cashAccountId, instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("ACCOUNT_ACTION_NOT_SUPPORTED")));

        var holdingsOnlyJson = """
                {"clientRequestId":"%s","name":"Holdings only","kind":"BROKERAGE","trackingMode":"HOLDINGS_ONLY","currency":"USD","timeZone":"UTC"}
                """.formatted(uuid("65000000-0000-4000-8000-000000000007"));
        var holdingsOnly = mockMvc.perform(post("/api/v1/accounts").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(holdingsOnlyJson))
                .andExpect(status().isCreated()).andReturn();
        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(idFrom(holdingsOnly), instrumentId, "BUY", "1", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("ACCOUNT_ACTION_NOT_SUPPORTED")));
    }

    @Test
    void cashProjectionOverflowReturnsStablePrecisionProblemWithoutWritingTradeFacts() throws Exception {
        var owner = testIdentitySupport.create("investing-cash-overflow-owner@example.com");
        var instrumentId = createInstrument(owner, "CASH-OVERFLOW-EQUITY", "EQUITY", "USD");
        var maximumCash = "99999999999999999999.999999999999999999";
        var maximumGross = "99999999999999999999.99";
        var brokerageId = createAccount(owner, uuid("65500000-0000-4000-8000-000000000001"), "Cash overflow brokerage", "BROKERAGE", maximumCash, "HARD_FLOOR");
        commitTrade(owner, uuid("65500000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "1", "1", "0", BUY_AT, 0);

        var activityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, owner.userId());
        var moneyPostingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());
        var securityPostingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());
        var idempotencyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());
        var cashVersion = jdbcTemplate.queryForObject("SELECT version FROM ledger.account_balance_projection WHERE financial_account_id = ?", Long.class,
                brokerageId);
        var positionVersion = jdbcTemplate.queryForObject("SELECT version FROM ledger.position_projection WHERE financial_account_id = ?", Long.class,
                brokerageId);

        mockMvc.perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(previewJson(brokerageId, instrumentId, "SELL", "1", maximumGross, "0", "CURRENT_ACTION", PARTIAL_SELL_AT, 1, false)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INVALID_SETTLED_PRECISION")));
        mockMvc.perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(commitJson(uuid("65500000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "SELL", "1", maximumGross, "0", "CURRENT_ACTION",
                        PARTIAL_SELL_AT, 1, false, cashVersion, positionVersion)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INVALID_SETTLED_PRECISION")));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(activityCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(moneyPostingCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(securityPostingCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(idempotencyCount);
        assertThat(jdbcTemplate.queryForObject("SELECT current_quantity FROM ledger.position_projection WHERE financial_account_id = ?", String.class,
                brokerageId)).isEqualTo("1.000000000000000000");
    }

    @Test
    void reversingBuyThatSupportsALaterSaleRollsBackAllTradeFacts() throws Exception {
        var owner = testIdentitySupport.create("investing-invalid-reversal-owner@example.com");
        var instrumentId = createInstrument(owner, "DEPENDENT-SELL-EQUITY", "EQUITY", "USD");
        var brokerageId = createAccount(owner, uuid("66000000-0000-4000-8000-000000000001"), "Dependent sale brokerage", "BROKERAGE", "200", "HARD_FLOOR");
        var buyPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "BUY", "10", "10", "0", "CURRENT_ACTION", BUY_AT, 0, false)))
                .andExpect(status().isOk()).andReturn();
        var buy = mockMvc
                .perform(post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("66000000-0000-4000-8000-000000000002"), brokerageId, instrumentId, "BUY", "10", "10", "0", "CURRENT_ACTION",
                                BUY_AT, 0, false, longJson(buyPreview, "$.cashBalanceVersion"), longJson(buyPreview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andReturn();
        var buyId = idFrom(buy);
        var sellAt = "2026-09-19T11:02:00Z";
        var sellPreview = mockMvc
                .perform(post("/api/v1/trades/previews").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(brokerageId, instrumentId, "SELL", "8", "15", "0", "CURRENT_ACTION", sellAt, 0, false)))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(
                post("/api/v1/trades").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(uuid("66000000-0000-4000-8000-000000000003"), brokerageId, instrumentId, "SELL", "8", "15", "0", "CURRENT_ACTION",
                                sellAt, 0, false, longJson(sellPreview, "$.cashBalanceVersion"), longJson(sellPreview, "$.positionVersion"))))
                .andExpect(status().isCreated());

        var activityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, owner.userId());
        var moneyPostingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());
        var securityPostingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());
        var idempotencyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", Integer.class,
                owner.userId());

        mockMvc.perform(post("/api/v1/activities/{activityId}/reversals", buyId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(uuid("66000000-0000-4000-8000-000000000004"), "The later sale depends on this buy")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("INSUFFICIENT_POSITION_QUANTITY")));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(activityCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(moneyPostingCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(securityPostingCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?", Integer.class, owner.userId()))
                .isEqualTo(idempotencyCount);
        mockMvc.perform(get("/api/v1/investing/positions/{accountId}/{instrumentId}", brokerageId, instrumentId).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity", equalTo("2"))).andExpect(jsonPath("$.remainingEconomicBasis", equalTo("20")));
    }

    private Accounts fundedBrokerage(Identity identity, String label, String amount) throws Exception {
        var source = createAccount(identity, UUID.randomUUID(), label + " source", "CASH_CURRENT", "1000", "HARD_FLOOR");
        var brokerage = createAccount(identity, UUID.randomUUID(), label + " brokerage", "BROKERAGE", "0", "HARD_FLOOR");
        mockMvc.perform(post("/api/v1/transfers").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(UUID.randomUUID(), source, brokerage, amount, FUNDED_AT))).andExpect(status().isCreated());
        return new Accounts(source, brokerage);
    }

    private UUID createAccount(Identity identity, UUID requestId, String name, String kind, String amount, String policy) throws Exception {
        var result = mockMvc.perform(post("/api/v1/accounts").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(accountJson(requestId, name, kind, amount, policy))).andExpect(status().isCreated()).andReturn();
        return idFrom(result);
    }

    private UUID createInstrument(Identity identity, String symbol, String type, String currency) throws Exception {
        var result = mockMvc.perform(post("/api/v1/reference/instruments").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(instrumentJson(symbol, type, currency))).andExpect(status().isCreated()).andReturn();
        return idFrom(result);
    }

    private MvcResult commitTrade(Identity identity, UUID requestId, UUID accountId, UUID instrumentId, String side, String quantity, String unitPrice,
            String commission, String effectiveAt, long sequence) throws Exception {
        var preview = mockMvc
                .perform(post("/api/v1/trades/previews").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(previewJson(accountId, instrumentId, side, quantity, unitPrice, commission, "CURRENT_ACTION", effectiveAt, sequence, false)))
                .andExpect(status().isOk()).andReturn();
        return mockMvc
                .perform(post("/api/v1/trades").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitJson(requestId, accountId, instrumentId, side, quantity, unitPrice, commission, "CURRENT_ACTION", effectiveAt, sequence,
                                false, longJson(preview, "$.cashBalanceVersion"), longJson(preview, "$.positionVersion"))))
                .andExpect(status().isCreated()).andReturn();
    }

    private UUID reconcile(Identity identity, UUID accountId, int closingBalance) throws Exception {
        var previewJson = """
                {"statementReference":"Lifecycle brokerage statement","statementOpeningAt":"2026-09-19T10:30:00Z","statementClosingAt":"2026-09-19T11:05:00Z","statementOpeningBalance":"500","statementClosingBalance":"%d"}
                """
                .formatted(closingBalance);
        mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliation-previews", accountId).with(identity.asBearer())
                .contentType(MediaType.APPLICATION_JSON).content(previewJson)).andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDifference", equalTo("0")));
        var projectionVersion = jdbcTemplate.queryForObject("SELECT version FROM ledger.account_balance_projection WHERE financial_account_id = ?", Long.class,
                accountId);
        var commitJson = """
                {"statementReference":"Lifecycle brokerage statement","statementOpeningAt":"2026-09-19T10:30:00Z","statementClosingAt":"2026-09-19T11:05:00Z","statementOpeningBalance":"500","statementClosingBalance":"%d","clientRequestId":"%s","expectedBalanceVersion":%d,"resolution":"CONFIRM_BALANCED"}
                """
                .formatted(closingBalance, uuid("61000000-0000-4000-8000-000000000020"), projectionVersion);
        var result = mockMvc.perform(post("/api/v1/accounts/{accountId}/reconciliations", accountId).with(identity.asBearer())
                .contentType(MediaType.APPLICATION_JSON).content(commitJson)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lifecycleStatus", equalTo("CURRENT"))).andReturn();
        return idFrom(result);
    }

    private void ensureGlobalInstrument() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.instrument WHERE id = ?", Integer.class, GLOBAL_INSTRUMENT_ID);
            if (count == null || count == 0) {
                var now = OBSERVED_AT.atOffset(ZoneOffset.UTC);
                jdbcTemplate
                        .update("INSERT INTO reference.instrument (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized," +
                                " instrument_type, quotation_currency_code, valuation_method, active, source_kind, version, created_at, updated_at)" +
                                " VALUES (?, NULL, ?, 'PR-GLOBAL-EQUITY', 'PR-GLOBAL-EQUITY', 'PR Global Equity', 'PR GLOBAL EQUITY'," +
                                " 'EQUITY', 'USD', 'NOT_VALUED', TRUE, 'REFERENCE_SEED', 0, ?, ?)", GLOBAL_INSTRUMENT_ID, MANUAL_MARKET_ID, now, now);
            }
        });
    }

    private static String accountJson(UUID requestId, String name, String kind, String amount, String policy) {
        return """
                {"clientRequestId":"%s","name":"%s","kind":"%s","trackingMode":"FULL_LEDGER","currency":"USD","timeZone":"UTC","policy":"%s","openingState":{"amount":"%s","effectiveAt":"%s"}}
                """
                .formatted(requestId, name, kind, policy, amount, OPENED_AT);
    }

    private static String instrumentJson(String symbol, String type, String currency) {
        return """
                {"marketId":"%s","symbol":"%s","name":"%s","instrumentType":"%s","quotationCurrency":"%s","valuationMethod":"NOT_VALUED"}
                """.formatted(MANUAL_MARKET_ID, symbol, symbol.replace('-', ' '), type, currency);
    }

    private static String previewJson(UUID accountId, UUID instrumentId, String side, String quantity, String unitPrice, String commission,
            String recordingMode, String effectiveAt, long sequence, boolean confirm) {
        return """
                {"accountId":"%s","instrumentId":"%s","side":"%s","quantity":"%s","unitPrice":"%s","commissionAmount":"%s","recordingMode":"%s","effectiveAt":"%s","economicSequence":%d,"confirmPolicyBreach":%s}
                """
                .formatted(accountId, instrumentId, side, quantity, unitPrice, commission, recordingMode, effectiveAt, sequence, confirm);
    }

    private static String commitJson(UUID requestId, UUID accountId, UUID instrumentId, String side, String quantity, String unitPrice, String commission,
            String recordingMode, String effectiveAt, long sequence, boolean confirm, long cashVersion, long positionVersion) {
        return """
                {"clientRequestId":"%s","accountId":"%s","instrumentId":"%s","side":"%s","quantity":"%s","unitPrice":"%s","commissionAmount":"%s","recordingMode":"%s","effectiveAt":"%s","economicSequence":%d,"confirmPolicyBreach":%s,"expectedCashBalanceVersion":%d,"expectedPositionVersion":%d}
                """
                .formatted(requestId, accountId, instrumentId, side, quantity, unitPrice, commission, recordingMode, effectiveAt, sequence, confirm,
                        cashVersion, positionVersion);
    }

    private static String transferJson(UUID requestId, UUID sourceId, UUID destinationId, String amount, String effectiveAt) {
        return """
                {"clientRequestId":"%s","sourceAccountId":"%s","destinationAccountId":"%s","amount":"%s","recordingMode":"CURRENT_ACTION","effectiveAt":"%s","confirmPolicyBreach":false}
                """
                .formatted(requestId, sourceId, destinationId, amount, effectiveAt);
    }

    private static String reversalJson(UUID requestId, String reason) {
        return """
                {"clientRequestId":"%s","correctionReason":"%s"}
                """.formatted(requestId, reason);
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static long longJson(MvcResult result, String path) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), path).longValue();
    }

    private static void assertTraceAndSession(MvcResult result) {
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record Accounts(UUID sourceId, UUID brokerageId) {}

}
