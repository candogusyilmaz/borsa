package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import dev.canverse.stocks.testing.TestIdentitySupport;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"stocks.identity.refresh-session.lifetime=2h"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class TradeImportHttpTest {

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    TestIdentitySupport testIdentitySupport;
    @Autowired
    TestClock testClock;
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant OPENED_AT = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant TRADE_AT = Instant.parse("2026-09-20T11:30:00Z");
    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final String HEADER = "external_id,side,instrument_id,currency,effective_at,economic_sequence,quantity,unit_price,commission_amount";
    @Autowired
    MockMvc mockMvc;

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    CashActivityCommandService cashActivityCommandService;

    @Autowired
    InvestingTradeCommandService tradeCommandService;

    @Autowired
    ManualInstrumentService instrumentService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetOwnerData() {
        testClock.setInstant(OBSERVED_AT);

        databaseCleaner.resetApplicationState();
    }

    @Test
    void authenticatedMultipartUploadPreviewCommitAndProvenanceAreExposedWithoutCaching() throws Exception {
        var owner = testIdentitySupport.create("trade-import-http-owner@example.com");
        var accountId = createBrokerage(owner.userId());
        var instrumentId = createInstrument(owner.userId());
        var content = (HEADER + "\nsource-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var firstKey = UUID.randomUUID();
        var uploadFile = new MockMultipartFile("file", "trades.csv", "text/csv", content);

        mockMvc.perform(multipart("/api/v1/imports").file(uploadFile).param("clientRequestId", firstKey.toString()).param("accountId", accountId.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/imports").param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString())
                .with(owner.asBearer())).andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "trades.csv", "text/csv", "wrong-header".getBytes()))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("IMPORT_FILE_FORMAT_INVALID")));

        var uploadResult = mockMvc
                .perform(multipart("/api/v1/imports").file(uploadFile).param("clientRequestId", firstKey.toString()).param("accountId", accountId.toString())
                        .with(owner.asBearer()))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.duplicateContent", equalTo(false))).andReturn();
        var batchId = UUID.fromString(JsonPath.<String>read(uploadResult.getResponse().getContentAsString(), "$.id"));
        var previewUrl = JsonPath.<String>read(uploadResult.getResponse().getContentAsString(), "$.previewUrl");
        org.assertj.core.api.Assertions.assertThat(uploadResult.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(previewUrl);

        mockMvc.perform(get(previewUrl).with(owner.asBearer())).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.commitEligible", equalTo(true))).andExpect(jsonPath("$.rows[0].externalId", equalTo("source-1")))
                .andExpect(jsonPath("$.summary.cashBalanceBefore", equalTo("500"))).andExpect(jsonPath("$.summary.cashBalanceAfter", equalTo("479")));
        var previewResult = mockMvc.perform(get(previewUrl).with(owner.asBearer())).andReturn();
        var previewToken = JsonPath.<String>read(previewResult.getResponse().getContentAsString(), "$.previewToken");

        var otherOwner = testIdentitySupport.create("trade-import-http-other@example.com");
        mockMvc.perform(get(previewUrl).with(otherOwner.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", batchId).with(otherOwner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"previewToken\":\"" + previewToken + "\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_NOT_FOUND")));
        var foreignAccountId = createBrokerage(otherOwner.userId());
        mockMvc.perform(multipart("/api/v1/imports").file(uploadFile).param("clientRequestId", UUID.randomUUID().toString())
                .param("accountId", foreignAccountId.toString()).with(owner.asBearer())).andExpect(status().isNotFound());

        var duplicateResult = mockMvc
                .perform(multipart("/api/v1/imports").file(uploadFile).param("clientRequestId", UUID.randomUUID().toString())
                        .param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION, previewUrl)).andExpect(jsonPath("$.id", equalTo(batchId.toString())))
                .andExpect(jsonPath("$.duplicateContent", equalTo(true))).andReturn();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(duplicateResult.getResponse().getContentAsString(), "$.previewUrl"))
                .isEqualTo(previewUrl);

        var commitKey = UUID.randomUUID();
        var commitBody = "{\"clientRequestId\":\"" + commitKey + "\",\"previewToken\":\"" + previewToken + "\"}";
        var commitResult = mockMvc
                .perform(post("/api/v1/imports/{batchId}/commit", batchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(commitBody))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status", equalTo("COMMITTED"))).andExpect(jsonPath("$.committedRowCount", equalTo(1)))
                .andExpect(jsonPath("$.cashBalanceAfter", equalTo("479"))).andReturn();
        var tradeId = UUID.fromString(JsonPath.<String>read(commitResult.getResponse().getContentAsString(), "$.activityIds[0]"));
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", batchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(commitBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activityIds[0]", equalTo(tradeId.toString())));
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", batchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"previewToken\":\"" + previewToken + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_ALREADY_COMMITTED")));
        mockMvc.perform(get("/api/v1/trades/{tradeId}", tradeId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.sourceImportBatchId", equalTo(batchId.toString())))
                .andExpect(jsonPath("$.sourceImportRowId").isNotEmpty()).andExpect(jsonPath("$.sourceExternalId", equalTo("source-1")));

        var manualEffectiveAt = TRADE_AT.plusSeconds(60);
        var manualPreview = tradeCommandService.preview(owner.userId(),
                new TradePreviewRequest(accountId, instrumentId, TradeSide.BUY, "1", "1", "0", RecordingMode.HISTORICAL_FACT, manualEffectiveAt, 2L, false));
        var manualTrade = tradeCommandService.commit(owner.userId(), new TradeCommitRequest(UUID.randomUUID(), accountId, instrumentId, TradeSide.BUY, "1", "1",
                "0", RecordingMode.HISTORICAL_FACT, manualEffectiveAt, 2L, false, manualPreview.cashBalanceVersion(), manualPreview.positionVersion()));
        var history = mockMvc.perform(get("/api/v1/trades").param("accountId", accountId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        var historyItems = JsonPath.<List<Map<String, Object>>>read(history.getResponse().getContentAsString(), "$.items");
        assertThat(historyItems).filteredOn(item -> tradeId.toString().equals(item.get("id"))).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("sourceImportBatchId", batchId.toString()).containsEntry("sourceExternalId", "source-1");
            assertThat(item.get("sourceImportRowId")).isNotNull();
        });
        assertThat(historyItems).filteredOn(item -> manualTrade.id().toString().equals(item.get("id"))).singleElement().satisfies(item -> {
            assertThat(item).containsKeys("sourceImportBatchId", "sourceImportRowId", "sourceExternalId");
            assertThat(item.get("sourceImportBatchId")).isNull();
            assertThat(item.get("sourceImportRowId")).isNull();
            assertThat(item.get("sourceExternalId")).isNull();
        });
    }

    @Test
    void malformedAndOversizedMultipartInputsUseSafeErrors() throws Exception {
        var owner = testIdentitySupport.create("trade-import-http-errors@example.com");
        var accountId = createBrokerage(owner.userId());
        mockMvc.perform(multipart("/api/v1/imports").param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString())
                .with(owner.asBearer())).andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("IMPORT_FILE_EMPTY")));
        var invalidUtf8 = new byte[]{(byte) 0xc3, (byte) 0x28};
        mockMvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "invalid.csv", "text/csv", invalidUtf8))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("IMPORT_FILE_ENCODING_INVALID")));
        mockMvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "invalid.csv", "text/csv", (HEADER + "\n\"unfinished").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("IMPORT_FILE_FORMAT_INVALID")));

        var tooManyRows = new StringBuilder(HEADER).append('\n');
        for (var index = 0; index <= 500; index++) {
            tooManyRows.append("row").append(index).append(",BUY,00000000-0000-4000-8000-000000000001,USD,").append(TRADE_AT).append(',').append(index)
                    .append(",1,1,0\n");
        }
        mockMvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "too-many.csv", "text/csv", tooManyRows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code", equalTo("IMPORT_ROW_LIMIT_EXCEEDED")));
        mockMvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "oversized.csv", "text/csv", new byte[1_048_577]))
                .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code", equalTo("PAYLOAD_TOO_LARGE")));
        mockMvc.perform(post("/api/v1/imports").contentType(MediaType.APPLICATION_JSON).content("{}").with(owner.asBearer()))
                .andExpect(status().isUnsupportedMediaType());

        var malformed = mockMvc
                .perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", "source-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isUnprocessableContent()).andReturn();
        assertThat(malformed.getResponse().getContentAsString()).doesNotContain("source-secret", "CSVParser", "org.apache.commons.csv");

        var instrumentId = createInstrument(owner.userId());
        var validCsv = HEADER + "\nsource-stale,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,1,10,0";
        var validUpload = mockMvc
                .perform(multipart("/api/v1/imports").file(uploadFile(validCsv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isCreated()).andReturn();
        var validBatchId = UUID.fromString(JsonPath.<String>read(validUpload.getResponse().getContentAsString(), "$.id"));
        var previewResponse = mockMvc.perform(get("/api/v1/imports/{batchId}/preview", validBatchId).with(owner.asBearer())).andExpect(status().isOk())
                .andReturn();
        var previewToken = JsonPath.<String>read(previewResponse.getResponse().getContentAsString(), "$.previewToken");
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", validBatchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"previewToken\":\"invalid\"}")).andExpect(status().isUnprocessableContent());
        cashActivityCommandService.recordCashActivity(owner.userId(), accountId, new CashActivityRequest(UUID.randomUUID(), ActivityType.CASH_DEPOSIT, "1",
                RecordingMode.HISTORICAL_FACT, OPENED_AT.plusSeconds(60), false, null));
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", validBatchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"previewToken\":\"" + previewToken + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_PREVIEW_STALE")));

        var invalidUpload = mockMvc
                .perform(multipart("/api/v1/imports").file(uploadFile((HEADER + "\nshort,row").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("clientRequestId", UUID.randomUUID().toString()).param("accountId", accountId.toString()).with(owner.asBearer()))
                .andExpect(status().isCreated()).andReturn();
        var invalidBatchId = UUID.fromString(JsonPath.<String>read(invalidUpload.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(post("/api/v1/imports/{batchId}/commit", invalidBatchId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"previewToken\":\"" + "a".repeat(64) + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_NOT_COMMITTABLE")));
    }

    private UUID createBrokerage(UUID ownerId) {
        var response = new TransactionTemplate(transactionManager).execute(
                status -> accountService.create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), "HTTP import brokerage", AccountKind.BROKERAGE,
                        TrackingMode.FULL_LEDGER, "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("500", OPENED_AT))));
        return java.util.Objects.requireNonNull(response).id();
    }

    private MockMultipartFile uploadFile(byte[] content) {
        return new MockMultipartFile("file", "trades.csv", "text/csv", content);
    }

    private UUID createInstrument(UUID ownerId) {
        var response = new TransactionTemplate(transactionManager).execute(status -> instrumentService.create(ownerId,
                new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "HTTP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "HTTP import equity",
                        InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of())));
        return java.util.Objects.requireNonNull(response).id();
    }

}
