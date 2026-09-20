package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.investing.application.InvestingQueryService;
import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.application.TradeImportService;
import dev.canverse.stocks.investing.domain.TradeImportIssueCode;
import dev.canverse.stocks.investing.domain.TradeImportStatus;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradeImportCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.investing.web.response.TradeImportUploadResponse;
import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.application.ReconciliationCommandService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationAction;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestClockConfiguration.class)
class TradeImportServiceTest {

    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    TestClock testClock;
    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant OPENED_AT = OBSERVED_AT.minusSeconds(3600);
    private static final Instant TRADE_AT = OBSERVED_AT.minusSeconds(1800);
    private static final String HEADER = "external_id,side,instrument_id,currency,effective_at,economic_sequence,quantity,unit_price,commission_amount";
    @Autowired
    TradeImportService tradeImportService;

    @Autowired
    InvestingQueryService investingQueryService;

    @Autowired
    InvestingTradeCommandService tradeCommandService;

    @Autowired
    CashActivityCommandService cashActivityCommandService;

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountQueryService accountQueryService;

    @Autowired
    ReconciliationCommandService reconciliationService;

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
    void resetDatabaseAndCreateTradeFixture() {
        testClock.setInstant(OBSERVED_AT);

        databaseCleaner.resetApplicationState();
        ownerId = insertUser();
        accountId = new TransactionTemplate(transactionManager)
                .execute(status -> accountService
                        .create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), "Import brokerage", AccountKind.BROKERAGE,
                                TrackingMode.FULL_LEDGER, "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("500", OPENED_AT)))
                        .id());
        instrumentId = new TransactionTemplate(transactionManager).execute(status -> instrumentService
                .create(ownerId, new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "IMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "Import equity", InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of()))
                .id());
    }

    @Test
    void previewsWithoutWritesThenCommitsEveryRowWithInspectableProvenance() {
        var csv = HEADER + "\nsource-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1";
        var request = upload(UUID.randomUUID(), csv.getBytes(StandardCharsets.UTF_8));
        var upload = tradeImportService.upload(ownerId, request.clientRequestId(), accountId, request.file());

        assertThat(upload.status()).isEqualTo(TradeImportStatus.PARSED);
        assertThat(upload.duplicateContent()).isFalse();
        var preview = tradeImportService.preview(ownerId, upload.id());
        assertThat(preview.commitEligible()).isTrue();
        assertThat(preview.summary().cashBalanceBefore()).isEqualTo("500");
        assertThat(preview.summary().cashBalanceAfter()).isEqualTo("479");
        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.externalId()).isEqualTo("source-1");
            assertThat(row.cashDelta()).isEqualTo("-21");
            assertThat(row.policyDecision()).isEqualTo(dev.canverse.stocks.ledger.domain.PolicyDecision.ALLOWED);
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type IN ('SECURITY_BUY','SECURITY_SELL')", Integer.class,
                ownerId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM ledger.account_balance_projection WHERE financial_account_id = ?", Long.class, accountId))
                .isZero();
        assertThat(tradeImportService.preview(ownerId, upload.id()).previewToken()).isEqualTo(preview.previewToken());

        var committed = tradeImportService.commit(ownerId, upload.id(), new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken()));
        assertThat(committed.status()).isEqualTo(TradeImportStatus.COMMITTED);
        assertThat(committed.committedRowCount()).isEqualTo(1);
        assertThat(committed.cashBalanceAfter()).isEqualTo("479");
        assertThat(jdbcTemplate.queryForObject("SELECT source_kind FROM ledger.activity WHERE id = ?", String.class, committed.activityIds().getFirst()))
                .isEqualTo("FILE_IMPORTED");
        assertThat(jdbcTemplate.queryForObject("SELECT source_import_row_id FROM ledger.activity WHERE id = ?", UUID.class, committed.activityIds().getFirst()))
                .isEqualTo(preview.rows().getFirst().id());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO ledger.activity (id, owner_user_account_id, client_event_id, operation_scope, command_sequence, correction_reason," +
                        " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision, economic_sequence, source_import_row_id)" +
                        " VALUES (?, ?, ?, 'test.import-duplicate-source', 0, NULL, 'SECURITY_BUY', 'HISTORICAL_FACT', ?, ?, 'FILE_IMPORTED', 'ALLOWED'," +
                        " ?, ?)",
                UUID.randomUUID(), ownerId, UUID.randomUUID(), preview.rows().getFirst().effectiveAt(), committed.committedAt(),
                preview.rows().getFirst().economicSequence(), preview.rows().getFirst().id()))).isInstanceOf(DataAccessException.class);
        var detail = investingQueryService.getTrade(ownerId, committed.activityIds().getFirst());
        assertThat(detail.sourceImportBatchId()).isEqualTo(upload.id());
        assertThat(detail.sourceImportRowId()).isEqualTo(preview.rows().getFirst().id());
        assertThat(detail.sourceExternalId()).isEqualTo("source-1");

        var committedPreview = tradeImportService.preview(ownerId, upload.id());
        assertThat(committedPreview.commitEligible()).isFalse();
        assertThat(committedPreview.previewToken()).isNull();
        assertThat(committedPreview.rows().getFirst().committedActivityId()).isEqualTo(committed.activityIds().getFirst());
    }

    @Test
    void exactFileContentConvergesAndStaticInvalidRowsRemainInspectable() {
        var validRow = "source-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1";
        var first = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId,
                upload(UUID.randomUUID(), (HEADER + "\n" + validRow).getBytes(StandardCharsets.UTF_8)).file());
        var second = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId,
                upload(UUID.randomUUID(), (HEADER + "\n" + validRow).getBytes(StandardCharsets.UTF_8)).file());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.duplicateContent()).isTrue();

        var invalid = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId,
                upload(UUID.randomUUID(), (HEADER + "\nshort,row").getBytes(StandardCharsets.UTF_8)).file());
        var preview = tradeImportService.preview(ownerId, invalid.id());
        assertThat(preview.commitEligible()).isFalse();
        assertThat(preview.previewToken()).isNull();
        assertThat(preview.rows().getFirst().issues()).extracting("code").containsExactly("RECORD_SHAPE_INVALID");
        assertThat(preview.rows().getFirst().side()).isNull();
    }

    @Test
    void rowFingerprintsIgnoreExternalIdAndCanonicalizeEquivalentEconomicValues() {
        var firstCsv = HEADER + "\n" + row("external-one", "BUY", instrumentId.toString(), "USD", TRADE_AT.toString(), "40", "2.00", "10.0", "0.00");
        var equivalentCsv = HEADER + "\n" + row("external-two", "BUY", instrumentId.toString(), "USD",
                "%s.000000Z".formatted(TRADE_AT.toString().substring(0, TRADE_AT.toString().length() - 1)), "40", "2", "10.00", "0");
        var changedCsv = HEADER + "\n" + row("external-three", "BUY", instrumentId.toString(), "USD", TRADE_AT.toString(), "40", "2.01", "10", "0");

        var first = uploadText(firstCsv);
        var equivalent = uploadText(equivalentCsv);
        var changed = uploadText(changedCsv);

        assertThat(tradeImportService.preview(ownerId, first.id()).rows().getFirst().rowFingerprint())
                .isEqualTo(tradeImportService.preview(ownerId, equivalent.id()).rows().getFirst().rowFingerprint())
                .isNotEqualTo(tradeImportService.preview(ownerId, changed.id()).rows().getFirst().rowFingerprint());
    }

    @Test
    void multiInstrumentCommitPreservesSourceOrderAndWritesEachProjectionOnce() {
        var secondInstrumentId = new TransactionTemplate(transactionManager).execute(status -> instrumentService
                .create(ownerId, new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "IMP-SECOND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "Second import equity", InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of()))
                .id());
        var later = TRADE_AT.plusSeconds(60);
        var csv = HEADER + "\n" + row("source-later", "BUY", instrumentId.toString(), "USD", later.toString(), "41", "2", "10", "1") + "\n" +
                row("source-earlier", "BUY", secondInstrumentId.toString(), "USD", TRADE_AT.toString(), "40", "3", "5", "0");
        var upload = uploadText(csv);
        var preview = tradeImportService.preview(ownerId, upload.id());

        assertThat(preview.commitEligible()).isTrue();
        assertThat(preview.positionImpacts()).hasSize(2);
        assertThat(preview.summary().cashBalanceAfter()).isEqualTo("464");
        var committed = tradeImportService.commit(ownerId, upload.id(), new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken()));

        assertThat(committed.cashBalanceAfter()).isEqualTo("464");
        assertThat(committed.committedRowCount()).isEqualTo(2);
        assertThat(committed.activityIds()).hasSize(2);
        assertThat(investingQueryService.getTrade(ownerId, committed.activityIds().get(0)).sourceExternalId()).isEqualTo("source-later");
        assertThat(investingQueryService.getTrade(ownerId, committed.activityIds().get(1)).sourceExternalId()).isEqualTo("source-earlier");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                Integer.class, ownerId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ? AND activity_id IN (?, ?)",
                Integer.class, ownerId, committed.activityIds().get(0), committed.activityIds().get(1))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ? AND activity_id IN (?, ?)",
                Integer.class, ownerId, committed.activityIds().get(0), committed.activityIds().get(1))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ? AND instrument_id IN (?, ?)",
                Integer.class, ownerId, instrumentId, secondInstrumentId)).isEqualTo(2);
    }

    @Test
    void maximumBatchWithFiveHundredInstrumentsCommitsAndReplaysWithinTheSnapshotLimit() {
        var instrumentIds = createInstruments(500);
        var csv = new StringBuilder(HEADER);
        for (var index = 0; index < instrumentIds.size(); index++) {
            csv.append('\n').append(
                    row("source-" + index, "BUY", instrumentIds.get(index).toString(), "USD", TRADE_AT.toString(), Integer.toString(index), "1", "1", "0"));
        }
        var request = upload(UUID.randomUUID(), csv.toString().getBytes(StandardCharsets.UTF_8));
        var uploaded = tradeImportService.upload(ownerId, request.clientRequestId(), accountId, request.file());
        var preview = tradeImportService.preview(ownerId, uploaded.id());

        assertThat(preview.commitEligible()).isTrue();
        assertThat(preview.rows()).hasSize(500);
        assertThat(preview.positionImpacts()).hasSize(500);
        var commitRequest = new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken());
        var committed = tradeImportService.commit(ownerId, uploaded.id(), commitRequest);
        var snapshotSize = jdbcTemplate.queryForObject(
                "SELECT octet_length(result_snapshot::text) FROM ledger.idempotency_record WHERE owner_user_account_id = ? AND operation_scope = ?" +
                        " AND client_request_id = ?",
                Integer.class, ownerId, "investing.trade_import.commit", commitRequest.clientRequestId());

        assertThat(committed.committedRowCount()).isEqualTo(500);
        assertThat(committed.activityIds()).hasSize(500).doesNotHaveDuplicates();
        assertThat(committed.positions()).hasSize(500).extracting("instrumentId").doesNotHaveDuplicates();
        assertThat(snapshotSize).isLessThanOrEqualTo(32_768);
        assertThat(tradeImportService.commit(ownerId, uploaded.id(), commitRequest)).isEqualTo(committed);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE client_event_id = ? AND source_kind = 'FILE_IMPORTED'",
                Integer.class, uploaded.id())).isEqualTo(500);
    }

    @Test
    void lateCommitIdempotencyConstraintFailureRollsBackAllFinancialWrites() {
        var uploaded = uploadRow("late-failure", "1", "10", "0", TRADE_AT, 90);
        var preview = tradeImportService.preview(ownerId, uploaded.id());
        var commitRequest = new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken());
        var constraintName = "ck_test_trade_import_late_failure";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.execute(
                "ALTER TABLE ledger.idempotency_record ADD CONSTRAINT " + constraintName + " CHECK (operation_scope <> 'investing.trade_import.commit')"));

        try {
            // The service flushes activities, postings, projections, and the batch before inserting this commit result.
            assertThatThrownBy(() -> tradeImportService.commit(ownerId, uploaded.id(), commitRequest))
                    .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class)
                    .satisfies(exception -> assertThat(((org.hibernate.exception.ConstraintViolationException) exception).getConstraintName())
                            .isEqualTo(constraintName));
        } finally {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> jdbcTemplate.execute("ALTER TABLE ledger.idempotency_record DROP CONSTRAINT IF EXISTS " + constraintName));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM ledger.trade_import_batch WHERE id = ?", String.class, uploaded.id()))
                .isEqualTo(TradeImportStatus.PARSED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT committed_at IS NULL AND version = 0 FROM ledger.trade_import_batch WHERE id = ?", Boolean.class,
                uploaded.id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE client_event_id = ? AND source_kind = 'FILE_IMPORTED'",
                Integer.class, uploaded.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.money_posting p JOIN ledger.activity a ON a.owner_user_account_id = p.owner_user_account_id" +
                        " AND a.id = p.activity_id WHERE a.client_event_id = ? AND a.operation_scope = 'investing.trade_import.commit'",
                Integer.class, uploaded.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.security_posting p JOIN ledger.activity a ON a.owner_user_account_id = p.owner_user_account_id" +
                        " AND a.id = p.activity_id WHERE a.client_event_id = ? AND a.operation_scope = 'investing.trade_import.commit'",
                Integer.class, uploaded.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE financial_account_id = ? AND instrument_id = ?",
                Integer.class, accountId, instrumentId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ledger_balance = 500 AND version = 0 FROM ledger.account_balance_projection WHERE financial_account_id = ?", Boolean.class, accountId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ? AND operation_scope = ?" + " AND client_request_id = ?",
                Integer.class, ownerId, "investing.trade_import.commit", commitRequest.clientRequestId())).isZero();
    }

    @Test
    void previewReplaysSourceRowsInEconomicOrderThroughPartialCloseAndReopen() {
        var csv = HEADER + "\n" + row("buy-first", "BUY", instrumentId.toString(), "USD", TRADE_AT.toString(), "1", "3", "10", "1") + "\n" +
                row("sell-second", "SELL", instrumentId.toString(), "USD", TRADE_AT.plusSeconds(2).toString(), "3", "2", "12", "1") + "\n" +
                row("reopen-third", "BUY", instrumentId.toString(), "USD", TRADE_AT.plusSeconds(4).toString(), "4", "1", "5", "0") + "\n" +
                row("close-fourth", "SELL", instrumentId.toString(), "USD", TRADE_AT.plusSeconds(3).toString(), "2", "1", "12", "0");
        var upload = uploadText(csv);
        var preview = tradeImportService.preview(ownerId, upload.id());

        assertThat(preview.commitEligible()).isTrue();
        assertThat(preview.summary().normalizedRowCount()).isEqualTo(4);
        assertThat(preview.summary().buyCount()).isEqualTo(2);
        assertThat(preview.summary().sellCount()).isEqualTo(2);
        assertThat(preview.summary().buyGross()).isEqualTo("35");
        assertThat(preview.summary().sellGross()).isEqualTo("36");
        assertThat(preview.summary().commissionTotal()).isEqualTo("2");
        assertThat(preview.summary().cashDeltaTotal()).isEqualTo("-1");
        assertThat(preview.summary().cashBalanceBefore()).isEqualTo("500");
        assertThat(preview.summary().cashBalanceAfter()).isEqualTo("499");
        assertThat(preview.positionImpacts()).singleElement().satisfies(impact -> {
            assertThat(impact.quantityBefore()).isEqualTo("0");
            assertThat(impact.quantityAfter()).isEqualTo("1");
            assertThat(impact.remainingBasisAfter()).isEqualTo("5");
            assertThat(impact.realizedEconomicPnlAfter()).isEqualTo("4");
        });

        var committed = tradeImportService.commit(ownerId, upload.id(), new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken()));
        assertThat(committed.activityIds()).hasSize(4);
        assertThat(investingQueryService.getTrade(ownerId, committed.activityIds().get(1)).sourceExternalId()).isEqualTo("sell-second");
        assertThat(investingQueryService.getTrade(ownerId, committed.activityIds().get(2)).sourceExternalId()).isEqualTo("reopen-third");
        assertThat(investingQueryService.getPosition(ownerId, accountId, instrumentId).quantity()).isEqualTo("1");
        assertThat(investingQueryService.getPosition(ownerId, accountId, instrumentId).remainingEconomicBasis()).isEqualTo("5");
        assertThat(investingQueryService.getPosition(ownerId, accountId, instrumentId).cumulativeRealizedEconomicPnl()).isEqualTo("4");
    }

    @Test
    void backdatedImportedCashPostingMarksThePreviouslyCurrentReconciliationStale() {
        var statementOpeningAt = OPENED_AT.plusSeconds(1);
        var statementClosingAt = TRADE_AT.plusSeconds(60);
        var reconciliationPreview = reconciliationService.preview(ownerId, accountId,
                new ReconciliationPreviewRequest("before-import", statementOpeningAt, statementClosingAt, "500", "500"));
        var reconciliation = reconciliationService.commit(ownerId, accountId, new ReconciliationCommitRequest("before-import", statementOpeningAt,
                statementClosingAt, "500", "500", UUID.randomUUID(), reconciliationPreview.projectionVersion(), ReconciliationAction.CONFIRM_BALANCED, null));
        assertThat(reconciliation.lifecycleStatus().name()).isEqualTo("CURRENT");

        var upload = uploadRow("backdated-import", "1", "10", "0", TRADE_AT, 1);
        var preview = tradeImportService.preview(ownerId, upload.id());
        assertThat(preview.commitEligible()).isTrue();
        tradeImportService.commit(ownerId, upload.id(), new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken()));

        assertThat(accountQueryService.balance(ownerId, accountId, null).lastReconciliation().lifecycleStatus().name()).isEqualTo("STALE");
    }

    @Test
    void shortProducingLaterRowPreventsEveryFinancialWrite() {
        var csv = HEADER + "\n" + row("buy-first", "BUY", instrumentId.toString(), "USD", TRADE_AT.toString(), "50", "1", "10", "0") + "\n" +
                row("sell-too-much", "SELL", instrumentId.toString(), "USD", TRADE_AT.plusSeconds(1).toString(), "51", "2", "10", "0");
        var upload = uploadText(csv);
        var preview = tradeImportService.preview(ownerId, upload.id());

        assertThat(preview.commitEligible()).isFalse();
        assertThat(preview.rows().get(1).issues()).extracting("code").containsExactly("INSUFFICIENT_POSITION_QUANTITY");
        assertThatThrownBy(() -> tradeImportService.commit(ownerId, upload.id(), new TradeImportCommitRequest(UUID.randomUUID(), "a".repeat(64))))
                .satisfies(exception -> assertThat(((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                        .isEqualTo(dev.canverse.stocks.investing.error.InvestingErrorCode.IMPORT_NOT_COMMITTABLE));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM ledger.trade_import_batch WHERE id = ?", String.class, upload.id()))
                .isEqualTo(TradeImportStatus.PARSED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                Integer.class, ownerId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM ledger.account_balance_projection WHERE financial_account_id = ?", Long.class, accountId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE financial_account_id = ?", Integer.class, accountId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ? AND operation_scope = ?",
                Integer.class, ownerId, "investing.trade_import.commit")).isZero();
    }

    @Test
    void changedUploadRequestKeyConflictsWhileDifferentBytesAndAccountsCreateDifferentBatches() {
        var requestId = UUID.randomUUID();
        var firstBytes = (HEADER + "\nsource-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8);
        var first = tradeImportService.upload(ownerId, requestId, accountId, upload(requestId, firstBytes).file());
        var exactRetry = tradeImportService.upload(ownerId, requestId, accountId, upload(requestId, firstBytes).file());
        assertThat(exactRetry.id()).isEqualTo(first.id());
        assertThat(exactRetry.duplicateContent()).isFalse();

        var changedBytes = (HEADER + "\nsource-2,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> tradeImportService.upload(ownerId, requestId, accountId, upload(requestId, changedBytes).file()))
                .satisfies(exception -> assertThat(((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                        .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT));

        var differentFile = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), changedBytes).file());
        var secondAccount = new TransactionTemplate(transactionManager).execute(
                status -> accountService.create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), "Second import brokerage", AccountKind.BROKERAGE,
                        TrackingMode.FULL_LEDGER, "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("500", OPENED_AT))));
        var otherAccountFile = tradeImportService.upload(ownerId, UUID.randomUUID(), java.util.Objects.requireNonNull(secondAccount).id(),
                upload(UUID.randomUUID(), firstBytes).file());

        assertThat(differentFile.id()).isNotEqualTo(first.id());
        assertThat(otherAccountFile.id()).isNotEqualTo(first.id());
        assertThat(otherAccountFile.accountId()).isNotEqualTo(first.accountId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isEqualTo(3);
    }

    @Test
    void persistsEveryStaticIssueWithNullNormalizedEconomics() {
        var unknownInstrument = "99999999-0000-4000-8000-000000000001";
        var otherOwnerId = insertUser();
        var privateInstrumentId = new TransactionTemplate(transactionManager).execute(status -> instrumentService.create(otherOwnerId,
                new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "PRIVATE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "Private import equity", InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of()))
                .id());
        var base = "2026-09-20T11:00:00Z";
        var duplicate = "same-economic-row,BUY," + instrumentId + ",USD," + base + ",30,1,1,0";
        var records = List.of("short,row", row("", "BUY", instrumentId.toString(), "USD", base, "1", "1", "1", "0"),
                row("bad-side", "buy", instrumentId.toString(), "USD", base, "2", "1", "1", "0"),
                row("bad-instrument-id", "BUY", "not-a-uuid", "USD", base, "3", "1", "1", "0"),
                row("missing-instrument", "BUY", unknownInstrument, "USD", base, "4", "1", "1", "0"),
                row("private-instrument", "BUY", privateInstrumentId.toString(), "USD", base, "41", "1", "1", "0"),
                row("bad-currency", "BUY", instrumentId.toString(), "ZZZ", base, "5", "1", "1", "0"),
                row("currency-mismatch", "BUY", instrumentId.toString(), "EUR", base, "6", "1", "1", "0"),
                row("bad-effective-at", "BUY", instrumentId.toString(), "USD", "2026-09-20T11:00:00+00:00", "7", "1", "1", "0"),
                row("submicrosecond-effective-at", "BUY", instrumentId.toString(), "USD", "2026-09-20T11:00:00.123456789Z", "42", "1", "1", "0"),
                row("bad-sequence", "BUY", instrumentId.toString(), "USD", base, "-1", "1", "1", "0"),
                row("bad-quantity", "BUY", instrumentId.toString(), "USD", base, "8", "0", "1", "0"),
                row("bad-price", "BUY", instrumentId.toString(), "USD", base, "9", "1", "-1", "0"),
                row("bad-commission", "BUY", instrumentId.toString(), "USD", base, "10", "1", "1", "-1"),
                row("settlement-overflow", "BUY", instrumentId.toString(), "USD", base, "11", "99999999999999999999", "99999999999999999999", "0"),
                row("no-sell-proceeds", "SELL", instrumentId.toString(), "USD", base, "12", "1", "1", "1"),
                row("reused-external-id", "BUY", instrumentId.toString(), "USD", base, "20", "1", "1", "0"),
                row("reused-external-id", "BUY", instrumentId.toString(), "USD", base, "21", "1", "1", "0"), duplicate,
                duplicate.replace("same-economic-row", "same-economic-row-again"));
        var file = HEADER + "\n" + String.join("\n", records);
        var upload = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), file.getBytes(StandardCharsets.UTF_8)).file());
        var preview = tradeImportService.preview(ownerId, upload.id());
        var observedCodes = preview.rows().stream().flatMap(row -> row.issues().stream()).map(issue -> issue.code()).distinct().toList();

        assertThat(preview.commitEligible()).isFalse();
        assertThat(preview.previewToken()).isNull();
        assertThat(observedCodes).containsExactlyInAnyOrderElementsOf(List.of(TradeImportIssueCode.values()).stream().map(Enum::name).toList());
        assertThat(preview.rows()).filteredOn(row -> !row.issues().isEmpty()).allSatisfy(row -> {
            assertThat(row.side()).isNull();
            assertThat(row.instrumentId()).isNull();
            assertThat(row.quantity()).isNull();
            assertThat(row.rowFingerprint()).isNull();
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_issue WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isGreaterThan(0);
    }

    @Test
    void reversedImportedTradesRemainDuplicateAndOwnerCleanupRemovesAllImportEvidence() {
        var originalBytes = (HEADER + "\nsource-1,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8);
        var uploaded = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), originalBytes).file());
        var preview = tradeImportService.preview(ownerId, uploaded.id());
        var commitRequest = new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken());
        var committed = tradeImportService.commit(ownerId, uploaded.id(), commitRequest);
        var replay = tradeImportService.commit(ownerId, uploaded.id(), commitRequest);
        var importedTradeId = committed.activityIds().getFirst();
        assertThat(replay).isEqualTo(committed);
        assertThat(replay.activityIds()).containsExactly(importedTradeId);
        assertThatThrownBy(
                () -> tradeImportService.commit(ownerId, uploaded.id(), new TradeImportCommitRequest(commitRequest.clientRequestId(), "b".repeat(64))))
                .satisfies(exception -> assertThat(((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                        .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT));

        cashActivityCommandService.reverse(ownerId, importedTradeId, new ReversalRequest(UUID.randomUUID(), "Correct imported trade"));
        assertThat(jdbcTemplate.queryForObject("SELECT source_kind FROM ledger.activity WHERE id = ?", String.class, importedTradeId))
                .isEqualTo("FILE_IMPORTED");
        var reversedDetail = investingQueryService.getTrade(ownerId, importedTradeId);
        assertThat(reversedDetail.sourceImportBatchId()).isEqualTo(uploaded.id());
        assertThat(reversedDetail.sourceImportRowId()).isEqualTo(preview.rows().getFirst().id());
        assertThat(reversedDetail.sourceExternalId()).isEqualTo("source-1");
        assertThat(jdbcTemplate.queryForObject("SELECT ledger_balance = 500 FROM ledger.account_balance_projection WHERE financial_account_id = ?",
                Boolean.class, accountId)).isTrue();
        assertThat(investingQueryService.getPosition(ownerId, accountId, instrumentId)).satisfies(position -> {
            assertThat(position.quantity()).isEqualTo("0");
            assertThat(position.remainingEconomicBasis()).isEqualTo("0");
            assertThat(position.cumulativeRealizedEconomicPnl()).isEqualTo("0");
        });
        assertThat(tradeImportService.preview(ownerId, uploaded.id()).rows().getFirst().committedActivityId()).isEqualTo(importedTradeId);
        var changedExternalId = (HEADER + "\nsource-2,BUY," + instrumentId + ",USD," + TRADE_AT + ",1,2,10,1").getBytes(StandardCharsets.UTF_8);
        var retry = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), changedExternalId).file());
        assertThat(retry.duplicateContent()).isFalse();
        var retryPreview = tradeImportService.preview(ownerId, retry.id());
        assertThat(retryPreview.rows().getFirst().issues()).extracting("code").contains("DUPLICATE_EXISTING_TRADE");
        assertThat(retryPreview.commitEligible()).isFalse();
        assertThatThrownBy(() -> tradeImportService.commit(ownerId, retry.id(), new TradeImportCommitRequest(UUID.randomUUID(), preview.previewToken())))
                .satisfies(exception -> assertThat(((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                        .isEqualTo(dev.canverse.stocks.investing.error.InvestingErrorCode.IMPORT_NOT_COMMITTABLE));

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", ownerId));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_row WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_issue WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, ownerId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?", Integer.class, ownerId)).isZero();
    }

    @Test
    void previewFindsManualDuplicatesOrderConflictsAndFutureRowsAndCommitRejectsAStaleToken() {
        var manualPreview = tradeCommandService.preview(ownerId,
                new TradePreviewRequest(accountId, instrumentId, TradeSide.BUY, "2", "10", "1", RecordingMode.HISTORICAL_FACT, TRADE_AT, 4L, false));
        var manualTrade = tradeCommandService.commit(ownerId, new TradeCommitRequest(UUID.randomUUID(), accountId, instrumentId, TradeSide.BUY, "2", "10", "1",
                RecordingMode.HISTORICAL_FACT, TRADE_AT, 4L, false, manualPreview.cashBalanceVersion(), manualPreview.positionVersion()));
        var manualTradeDetails = investingQueryService.getTrade(ownerId, manualTrade.id());
        assertThat(manualTradeDetails.sourceKind()).isEqualTo("USER_ENTERED");
        assertThat(manualTradeDetails.sourceImportBatchId()).isNull();
        assertThat(manualTradeDetails.sourceImportRowId()).isNull();
        assertThat(manualTradeDetails.sourceExternalId()).isNull();

        var duplicateBatch = uploadRow("manual-duplicate", "2", "10", "1", TRADE_AT, 4);
        var duplicatePreview = tradeImportService.preview(ownerId, duplicateBatch.id());
        assertThat(duplicatePreview.rows().getFirst().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("DUPLICATE_EXISTING_TRADE");
            assertThat(issue.relatedActivityId()).isEqualTo(manualTrade.id());
        });

        var conflictingBatch = uploadRow("manual-conflict", "3", "10", "1", TRADE_AT, 4);
        var conflict = tradeImportService.preview(ownerId, conflictingBatch.id());
        assertThat(conflict.rows().getFirst().issues()).extracting("code").containsExactly("EXISTING_ECONOMIC_ORDER_CONFLICT");

        var futureFile = HEADER + "\nfuture,BUY," + instrumentId + ",USD," + OBSERVED_AT.plusSeconds(60) + ",5,1,1,0";
        var futureBatch = tradeImportService.upload(ownerId, UUID.randomUUID(), accountId,
                upload(UUID.randomUUID(), futureFile.getBytes(StandardCharsets.UTF_8)).file());
        var futurePreview = tradeImportService.preview(ownerId, futureBatch.id());
        assertThat(futurePreview.rows().getFirst().issues()).extracting("code").containsExactly("EFFECTIVE_AT_FUTURE");
        assertThat(futurePreview.previewToken()).isNull();

        var staleBatch = uploadRow("stale-preview", "1", "10", "0", TRADE_AT.plusSeconds(1), 5);
        var stalePreview = tradeImportService.preview(ownerId, staleBatch.id());
        cashActivityCommandService.recordCashActivity(ownerId, accountId, new CashActivityRequest(UUID.randomUUID(), ActivityType.CASH_DEPOSIT, "1",
                RecordingMode.HISTORICAL_FACT, OPENED_AT.plusSeconds(60), false, null));
        assertThatThrownBy(
                () -> tradeImportService.commit(ownerId, staleBatch.id(), new TradeImportCommitRequest(UUID.randomUUID(), stalePreview.previewToken())))
                .satisfies(exception -> assertThat(((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                        .isEqualTo(dev.canverse.stocks.investing.error.InvestingErrorCode.IMPORT_PREVIEW_STALE));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND source_kind = 'FILE_IMPORTED'",
                Integer.class, ownerId)).isZero();
    }

    private static String row(String externalId, String side, String instrument, String currency, String effectiveAt, String sequence, String quantity,
            String unitPrice, String commission) {
        return String.join(",", externalId, side, instrument, currency, effectiveAt, sequence, quantity, unitPrice, commission);
    }

    private TradeImportUploadResponse uploadRow(String externalId, String quantity, String unitPrice, String commission, Instant effectiveAt, long sequence) {
        var file = HEADER + "\n" +
                row(externalId, "BUY", instrumentId.toString(), "USD", effectiveAt.toString(), Long.toString(sequence), quantity, unitPrice, commission);
        return tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), file.getBytes(StandardCharsets.UTF_8)).file());
    }

    private TradeImportUploadResponse uploadText(String file) {
        return tradeImportService.upload(ownerId, UUID.randomUUID(), accountId, upload(UUID.randomUUID(), file.getBytes(StandardCharsets.UTF_8)).file());
    }

    private List<UUID> createInstruments(int count) {
        return java.util.Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
            var ids = new java.util.ArrayList<UUID>(count);
            for (var index = 0; index < count; index++) {
                var response = instrumentService.create(ownerId, new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "BATCH-" + index,
                        "Batch import instrument " + index, InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of()));
                ids.add(response.id());
            }
            return List.copyOf(ids);
        }));
    }

    private UploadRequest upload(UUID clientRequestId, byte[] bytes) {
        return new UploadRequest(clientRequestId, new MockMultipartFile("file", "C:\\broker\\trades.csv", "text/csv", bytes));
    }

    private UUID insertUser() {
        var userId = UUID.randomUUID();
        var email = userId + "@trade-import.test";
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                        userId, email, email, now, now));
        return userId;
    }

    private record UploadRequest(UUID clientRequestId, MockMultipartFile file) {}

}
