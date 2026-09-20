package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.application.TradeImportService;
import dev.canverse.stocks.investing.domain.TradeImportStatus;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradeImportCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.investing.web.response.TradeImportUploadResponse;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class TradeImportConcurrencyTest {

    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant TRADE_AT = OBSERVED_AT.minusSeconds(1800);
    private static final String HEADER = "external_id,side,instrument_id,currency,effective_at,economic_sequence,quantity,unit_price,commission_amount";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    TradeImportService tradeImportService;

    @Autowired
    InvestingTradeCommandService tradeCommandService;

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    ManualInstrumentService instrumentService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private UUID ownerId;
    private UUID accountId;
    private UUID instrumentId;

    @BeforeEach
    void resetDatabaseAndCreateBrokerage() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> jdbcTemplate.execute("TRUNCATE TABLE identity.device_session, identity.auth_identity, identity.user_account CASCADE"));
        ownerId = insertUser();
        var account = new TransactionTemplate(transactionManager).execute(status -> accountService.create(ownerId,
                new CreateFinancialAccountRequest(UUID.randomUUID(), "Concurrent import brokerage", AccountKind.BROKERAGE, TrackingMode.FULL_LEDGER, "USD",
                        "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("500", OBSERVED_AT.minusSeconds(3600)))));
        accountId = Objects.requireNonNull(account).id();
        var instrument = new TransactionTemplate(transactionManager).execute(status -> instrumentService.create(ownerId,
                new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "CONC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "Concurrent import equity", InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of())));
        instrumentId = Objects.requireNonNull(instrument).id();
    }

    @Test
    void concurrentExactUploadsConvergeAndConcurrentCommitsPostOnce() throws Exception {
        var bytes = (HEADER + "\nsource-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> uploadAfter(start, UUID.randomUUID(), bytes));
            var second = executor.submit(() -> uploadAfter(start, UUID.randomUUID(), bytes));
            start.countDown();
            var firstUpload = first.get(30, TimeUnit.SECONDS);
            var secondUpload = second.get(30, TimeUnit.SECONDS);

            assertThat(firstUpload.id()).isEqualTo(secondUpload.id());
            assertThat(List.of(firstUpload.duplicateContent(), secondUpload.duplicateContent())).containsExactlyInAnyOrder(false, true);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_row WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(1);

            var preview = tradeImportService.preview(ownerId, firstUpload.id());
            assertThat(preview.commitEligible()).isTrue();
            var commitStart = new CountDownLatch(1);
            var firstCommit = executor.submit(() -> commitAfter(commitStart, firstUpload.id(), UUID.randomUUID(), preview.previewToken()));
            var secondCommit = executor.submit(() -> commitAfter(commitStart, firstUpload.id(), UUID.randomUUID(), preview.previewToken()));
            commitStart.countDown();
            var outcomes = List.of(firstCommit.get(30, TimeUnit.SECONDS), secondCommit.get(30, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(CommitOutcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(CommitOutcome::errorCode).filter(Objects::nonNull))
                    .containsExactly(InvestingErrorCode.IMPORT_ALREADY_COMMITTED.getCode());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                    Integer.class, ownerId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM ledger.trade_import_batch WHERE id = ?", String.class, firstUpload.id()))
                    .isEqualTo(TradeImportStatus.COMMITTED.name());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void manualTradeAndImportSerializeAgainstTheSameAccountSnapshot() throws Exception {
        var imported = uploadBatch(accountId, TRADE_AT.plusSeconds(1), 2, "imported-row");
        var preview = tradeImportService.preview(ownerId, imported.id());
        var manualPreview = tradeCommandService.preview(ownerId,
                new TradePreviewRequest(accountId, instrumentId, TradeSide.BUY, "1", "10", "0", RecordingMode.HISTORICAL_FACT, TRADE_AT, 1L, false));
        var manualRequest = new TradeCommitRequest(UUID.randomUUID(), accountId, instrumentId, TradeSide.BUY, "1", "10", "0", RecordingMode.HISTORICAL_FACT,
                TRADE_AT, 1L, false, manualPreview.cashBalanceVersion(), manualPreview.positionVersion());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var importCommit = executor.submit(() -> commitAfter(start, imported.id(), UUID.randomUUID(), preview.previewToken()));
            var manualCommit = executor.submit(() -> manualCommitAfter(start, manualRequest));
            start.countDown();
            var outcomes = List.of(importCommit.get(30, TimeUnit.SECONDS), manualCommit.get(30, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(CommitOutcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()).map(CommitOutcome::errorCode).toList()).containsAnyOf(
                    InvestingErrorCode.IMPORT_PREVIEW_STALE.getCode(), dev.canverse.stocks.ledger.error.LedgerErrorCode.BALANCE_VERSION_CONFLICT.getCode());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND" + " activity_type IN ('SECURITY_BUY','SECURITY_SELL')",
                    Integer.class, ownerId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDifferentBatchesOnOneAccountChooseOnePreviewSnapshot() throws Exception {
        var first = uploadBatch(accountId, TRADE_AT, 1, "first-batch-row");
        var second = uploadBatch(accountId, TRADE_AT.plusSeconds(1), 2, "second-batch-row");
        var firstPreview = tradeImportService.preview(ownerId, first.id());
        var secondPreview = tradeImportService.preview(ownerId, second.id());
        assertThat(firstPreview.commitEligible()).isTrue();
        assertThat(secondPreview.commitEligible()).isTrue();

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstCommit = executor.submit(() -> commitAfter(start, first.id(), UUID.randomUUID(), firstPreview.previewToken()));
            var secondCommit = executor.submit(() -> commitAfter(start, second.id(), UUID.randomUUID(), secondPreview.previewToken()));
            start.countDown();
            var outcomes = List.of(firstCommit.get(30, TimeUnit.SECONDS), secondCommit.get(30, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(CommitOutcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()).map(CommitOutcome::errorCode).toList())
                    .containsExactly(InvestingErrorCode.IMPORT_PREVIEW_STALE.getCode());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                    Integer.class, ownerId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch WHERE owner_user_account_id = ? AND status = 'COMMITTED'",
                    Integer.class, ownerId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCommitsOnIndependentAccountsBothComplete() throws Exception {
        var secondAccountId = createBrokerage("Independent import brokerage");
        var first = uploadBatch(accountId, TRADE_AT, 1, "same-content-row");
        var second = tradeImportService.upload(ownerId, UUID.randomUUID(), secondAccountId, new MockMultipartFile("file", "concurrent.csv", "text/csv",
                (HEADER + "\nsame-content-row,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8)));
        assertThat(second.id()).isNotEqualTo(first.id());
        var firstPreview = tradeImportService.preview(ownerId, first.id());
        var secondPreview = tradeImportService.preview(ownerId, second.id());

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstCommit = executor.submit(() -> commitAfter(start, first.id(), UUID.randomUUID(), firstPreview.previewToken()));
            var secondCommit = executor.submit(() -> commitAfter(start, second.id(), UUID.randomUUID(), secondPreview.previewToken()));
            start.countDown();
            var outcomes = List.of(firstCommit.get(30, TimeUnit.SECONDS), secondCommit.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.succeeded()).isTrue());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                    Integer.class, ownerId)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private TradeImportUploadResponse uploadAfter(CountDownLatch start, UUID clientRequestId, byte[] bytes) {
        await(start);
        return tradeImportService.upload(ownerId, clientRequestId, accountId, new MockMultipartFile("file", "concurrent.csv", "text/csv", bytes));
    }

    private CommitOutcome commitAfter(CountDownLatch start, UUID batchId, UUID clientRequestId, String previewToken) {
        await(start);
        try {
            tradeImportService.commit(ownerId, batchId, new TradeImportCommitRequest(clientRequestId, previewToken));
            return new CommitOutcome(true, null);
        } catch (AppException exception) {
            return new CommitOutcome(false, exception.getCode());
        }
    }

    private CommitOutcome manualCommitAfter(CountDownLatch start, TradeCommitRequest request) {
        await(start);
        try {
            tradeCommandService.commit(ownerId, request);
            return new CommitOutcome(true, null);
        } catch (AppException exception) {
            return new CommitOutcome(false, exception.getCode());
        }
    }

    private TradeImportUploadResponse uploadBatch(UUID targetAccountId, Instant effectiveAt, long sequence, String externalId) {
        var csv = HEADER + "\n" + externalId + ",BUY," + instrumentId + ",USD," + effectiveAt + "," + sequence + ",2,10,1";
        return tradeImportService.upload(ownerId, UUID.randomUUID(), targetAccountId,
                new MockMultipartFile("file", "concurrent.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID createBrokerage(String name) {
        var account = new TransactionTemplate(transactionManager).execute(status -> accountService.create(ownerId,
                new CreateFinancialAccountRequest(UUID.randomUUID(), name, AccountKind.BROKERAGE, TrackingMode.FULL_LEDGER, "USD", "UTC",
                        NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("500", OBSERVED_AT.minusSeconds(3600)))));
        return Objects.requireNonNull(account).id();
    }

    private UUID insertUser() {
        var userId = UUID.randomUUID();
        var email = userId + "@trade-import-concurrency.test";
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                        userId, email, email, now, now));
        return userId;
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent import test did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent import test interrupted", exception);
        }
    }

    private record CommitOutcome(boolean succeeded, String errorCode) {}
}
