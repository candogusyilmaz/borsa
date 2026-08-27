package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.CashTransferService;
import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.application.ReconciliationCommandService;
import dev.canverse.stocks.ledger.application.ReconciliationReadService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.ArchiveAccountRequest;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationAction;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.ledger.web.request.TransferPreviewRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
class LedgerReconciliationServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountLifecycleService accountLifecycleService;

    @Autowired
    FinancialAccountQueryService accountQueryService;

    @Autowired
    CashActivityCommandService activityService;

    @Autowired
    CashTransferService transferService;

    @Autowired
    ReconciliationCommandService reconciliationService;

    @Autowired
    ReconciliationReadService reconciliationReadService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void previewRetainsPeriodIntegrityWhenControllerValidationIsBypassed() {
        assertThatThrownBy(() -> reconciliationService.preview(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationPreviewRequest(
                                "invalid-period",
                                Instant.parse("2026-08-17T11:30:00Z"),
                                Instant.parse("2026-08-17T11:00:00Z"),
                                "100",
                                "100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Statement opening must precede closing");
    }

    @Test
    void futureReconciliationOpeningAndClosingUseCapabilityErrorAcrossWorkflows() {
        var now = Instant.now();
        var past = now.minusSeconds(60);
        var future = now.plusSeconds(60);

        assertThatThrownBy(() -> reconciliationService.preview(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationPreviewRequest(
                                "future-preview-opening", future, future.plusSeconds(60), "100", "100")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        assertThatThrownBy(() -> reconciliationService.preview(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationPreviewRequest("future-preview-closing", past, future, "100", "100")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);

        assertThatThrownBy(() -> reconciliationService.commit(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationCommitRequest(
                                "future-commit-opening",
                                future,
                                future.plusSeconds(60),
                                "100",
                                "100",
                                UUID.randomUUID(),
                                0L,
                                ReconciliationAction.CONFIRM_BALANCED,
                                null)))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        assertThatThrownBy(() -> reconciliationService.commit(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationCommitRequest(
                                "future-commit-closing",
                                past,
                                future,
                                "100",
                                "100",
                                UUID.randomUUID(),
                                0L,
                                ReconciliationAction.CONFIRM_BALANCED,
                                null)))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);

        assertThatThrownBy(() -> reconciliationService.correct(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationCorrectionRequest(
                                "future-correction-opening",
                                future,
                                future.plusSeconds(60),
                                "100",
                                "100",
                                UUID.randomUUID(),
                                0L,
                                ReconciliationAction.CONFIRM_BALANCED,
                                null,
                                "Future correction")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
        assertThatThrownBy(() -> reconciliationService.correct(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new ReconciliationCorrectionRequest(
                                "future-correction-closing",
                                past,
                                future,
                                "100",
                                "100",
                                UUID.randomUUID(),
                                0L,
                                ReconciliationAction.CONFIRM_BALANCED,
                                null,
                                "Future correction")))
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
    }

    @Test
    void previewUsesInclusiveAsOfBoundariesAndBalancedCommitCreatesNoMoney() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-balanced@example.com");
        var account = createAccount(ownerId, "Balanced", "100", times.openingAt());
        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "25.500",
                        dev.canverse.stocks.ledger.domain.RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));

        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "125.5"));

        assertThat(preview.statementOpeningBalance()).isEqualTo("100");
        assertThat(preview.ledgerOpeningBalance()).isEqualTo("100");
        assertThat(preview.ledgerClosingBalanceBeforeAdjustment()).isEqualTo("125.5");
        assertThat(preview.periodNetPostedAmount()).isEqualTo("25.5");
        assertThat(preview.periodPostingCount()).isEqualTo(1);
        assertThat(preview.totalPostingCountThroughClosing()).isEqualTo(2);
        assertThat(preview.closingDifference()).isEqualTo("0");
        assertThat(preview.admissibleResolutions()).containsExactly("CONFIRM_BALANCED");

        var committed = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "balanced-statement",
                        times.openingAt(),
                        times.closingAt(),
                        "100.00",
                        "125.500",
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CONFIRM_BALANCED,
                        null));

        assertThat(committed.resolution()).hasToString("BALANCED");
        assertThat(committed.adjustmentActivityId()).isNull();
        assertThat(committed.lifecycleStatus()).hasToString("CURRENT");
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("125.5");
        assertThat(count("ledger.reconciliation", ownerId)).isEqualTo(1);
        assertThat(count("ledger.activity", ownerId)).isEqualTo(2);
        assertThat(count("ledger.money_posting", ownerId)).isEqualTo(2);
        assertThat(accountQueryService
                        .balance(ownerId, account.id(), null)
                        .lastReconciliation()
                        .reconciliationId())
                .isEqualTo(committed.id());
    }

    @Test
    void adjustedCommitUsesExplicitActionAndExactSignedDifference() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-adjusted@example.com");
        var account = createAccount(ownerId, "Adjusted", "100", times.openingAt());
        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "25",
                        dev.canverse.stocks.ledger.domain.RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "130"));

        var committed = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "adjusted-statement",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "130.00",
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "  Unexplained statement difference  "));

        assertThat(committed.resolution()).hasToString("ADJUSTED");
        assertThat(committed.closingDifference()).isEqualTo("5");
        assertThat(committed.adjustmentAmount()).isEqualTo("5");
        assertThat(committed.adjustmentReason()).isEqualTo("Unexplained statement difference");
        assertThat(committed.totalPostingCountThroughClosing()).isEqualTo(3);
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("130");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT activity_type FROM ledger.activity WHERE id = ?",
                        String.class,
                        committed.adjustmentActivityId()))
                .isEqualTo("RECONCILIATION_ADJUSTMENT");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT amount FROM ledger.money_posting WHERE activity_id = ?",
                        java.math.BigDecimal.class,
                        committed.adjustmentActivityId()))
                .isEqualByComparingTo("5");
        assertThatThrownBy(() -> activityService.reverse(
                        ownerId,
                        committed.adjustmentActivityId(),
                        new ReversalRequest(UUID.randomUUID(), "Must use reconciliation correction")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
    }

    @Test
    void exactRetryReplaysOriginalAndCorrectionReversesThenSupersedesAdjustment() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-correction@example.com");
        var account = createAccount(ownerId, "Correction", "100", times.openingAt());
        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "25",
                        dev.canverse.stocks.ledger.domain.RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "130"));
        var clientRequestId = UUID.randomUUID();
        var commitRequest = new ReconciliationCommitRequest(
                "original-statement",
                times.openingAt(),
                times.closingAt(),
                "100",
                "130",
                clientRequestId,
                preview.projectionVersion(),
                ReconciliationAction.CREATE_ADJUSTMENT,
                "Original unexplained difference");
        var original = reconciliationService.commit(ownerId, account.id(), commitRequest);
        var replay = reconciliationService.commit(ownerId, account.id(), commitRequest);
        assertThat(replay.id()).isEqualTo(original.id());

        var replacementPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "127"));
        var replacement = reconciliationService.correct(
                ownerId,
                original.id(),
                new ReconciliationCorrectionRequest(
                        "corrected-statement",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "127",
                        UUID.randomUUID(),
                        replacementPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "Corrected unexplained difference",
                        "Corrected statement boundary"));

        assertThat(replacement.supersedesReconciliationId()).isEqualTo(original.id());
        assertThat(replacement.lifecycleStatus()).hasToString("CURRENT");
        assertThat(replacement.closingDifference()).isEqualTo("2");
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("127");
        assertThat(accountQueryService
                        .balance(ownerId, account.id(), null)
                        .lastReconciliation()
                        .reconciliationId())
                .isEqualTo(replacement.id());
        assertThat(reconciliationReadService.detail(ownerId, original.id()).lifecycleStatus())
                .hasToString("SUPERSEDED");
        assertThat(count("ledger.reconciliation", ownerId)).isEqualTo(2);
        assertThat(count("ledger.activity", ownerId)).isEqualTo(5);
        assertThat(count("ledger.money_posting", ownerId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'REVERSAL'",
                        Integer.class,
                        ownerId))
                .isEqualTo(1);
    }

    @Test
    void exactRetryReplaysAfterLaterStateChangeAndMaterialReuseConflicts() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-retry-state@example.com");
        var account = createAccount(ownerId, "Retry after state", "100", times.openingAt());
        var initialPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "100"));
        var request = new ReconciliationCommitRequest(
                "retry-state",
                times.openingAt(),
                times.closingAt(),
                "100",
                "100",
                UUID.randomUUID(),
                initialPreview.projectionVersion(),
                ReconciliationAction.CONFIRM_BALANCED,
                null);
        var original = reconciliationService.commit(ownerId, account.id(), request);

        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.closingAt().plusSeconds(1),
                        false,
                        null));

        assertThat(reconciliationService.commit(ownerId, account.id(), request).id())
                .isEqualTo(original.id());
        assertThatThrownBy(() -> reconciliationService.commit(
                        ownerId,
                        account.id(),
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                "101",
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                ReconciliationAction.CREATE_ADJUSTMENT,
                                "changed meaning")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @ParameterizedTest(name = "commit fingerprint: {0}")
    @MethodSource("commitFingerprintVariants")
    void commitRejectsEveryChangedFingerprintField(String variantName) {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-commit-fingerprint-" + variantName.replace(' ', '-') + "@example.com");
        var account = createAccount(ownerId, "Commit fingerprint " + variantName, "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "105"));
        var request = new ReconciliationCommitRequest(
                "fingerprint-commit",
                times.openingAt(),
                times.closingAt(),
                "100",
                "105",
                UUID.randomUUID(),
                preview.projectionVersion(),
                ReconciliationAction.CREATE_ADJUSTMENT,
                "matrix reason");
        reconciliationService.commit(ownerId, account.id(), request);

        var targetAccountId = account.id();
        var variant =
                switch (variantName) {
                    case "account" -> {
                        var other = createAccount(ownerId, "Commit fingerprint other", "100", times.openingAt());
                        targetAccountId = other.id();
                        yield request;
                    }
                    case "reference" ->
                        new ReconciliationCommitRequest(
                                "changed reference",
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason());
                    case "opening instant" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt().plusSeconds(1),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason());
                    case "closing instant" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt().minusSeconds(1),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason());
                    case "opening balance" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                "101",
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason());
                    case "closing balance" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                "106",
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason());
                    case "expected balance version" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion() + 1,
                                request.resolution(),
                                request.adjustmentReason());
                    case "resolution" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                ReconciliationAction.CONFIRM_BALANCED,
                                null);
                    case "adjustment reason" ->
                        new ReconciliationCommitRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                "another matrix reason");
                    default -> throw new IllegalArgumentException("Unknown commit fingerprint variant: " + variantName);
                };

        var commitTargetAccountId = targetAccountId;
        var commitVariant = variant;
        assertThatThrownBy(() -> reconciliationService.commit(ownerId, commitTargetAccountId, commitVariant))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @ParameterizedTest(name = "correction fingerprint: {0}")
    @MethodSource("correctionFingerprintVariants")
    void correctionRejectsEveryChangedFingerprintField(String variantName) {
        var times = Times.create();
        var ownerId =
                insertUser("reconciliation-correction-fingerprint-" + variantName.replace(' ', '-') + "@example.com");
        var account = createAccount(ownerId, "Correction fingerprint " + variantName, "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "105"));
        var original = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "fingerprint-original",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "105",
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "original difference"));
        var otherTarget = commitBalanced(
                        ownerId,
                        createAccount(ownerId, "Correction fingerprint other target", "100", times.openingAt())
                                .id(),
                        times,
                        "100",
                        "target")
                .id();
        var replacementPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "104"));
        var request = new ReconciliationCorrectionRequest(
                "fingerprint-replacement",
                times.openingAt(),
                times.closingAt(),
                "100",
                "104",
                UUID.randomUUID(),
                replacementPreview.projectionVersion(),
                ReconciliationAction.CREATE_ADJUSTMENT,
                "replacement difference",
                "replacement correction");
        reconciliationService.correct(ownerId, original.id(), request);

        var targetId = original.id();
        var variant =
                switch (variantName) {
                    case "target" -> request;
                    case "reference" ->
                        new ReconciliationCorrectionRequest(
                                "changed correction reference",
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "opening instant" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt().plusSeconds(1),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "closing instant" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt().minusSeconds(1),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "opening balance" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                "101",
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "closing balance" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                "103",
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "expected balance version" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion() + 1,
                                request.resolution(),
                                request.adjustmentReason(),
                                request.correctionReason());
                    case "resolution" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                ReconciliationAction.CONFIRM_BALANCED,
                                null,
                                request.correctionReason());
                    case "adjustment reason" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                "another adjustment reason",
                                request.correctionReason());
                    case "correction reason" ->
                        new ReconciliationCorrectionRequest(
                                request.statementReference(),
                                request.statementOpeningAt(),
                                request.statementClosingAt(),
                                request.statementOpeningBalance(),
                                request.statementClosingBalance(),
                                request.clientRequestId(),
                                request.expectedBalanceVersion(),
                                request.resolution(),
                                request.adjustmentReason(),
                                "another correction reason");
                    default ->
                        throw new IllegalArgumentException("Unknown correction fingerprint variant: " + variantName);
                };
        if (variantName.equals("target")) {
            targetId = otherTarget;
        }

        var correctionTargetId = targetId;
        var correctionVariant = variant;
        assertThatThrownBy(() -> reconciliationService.correct(ownerId, correctionTargetId, correctionVariant))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @ParameterizedTest(name = "commit decimal scale {0}")
    @MethodSource("decimalScales")
    void commitReplaysNumericallyEquivalentDecimalScales(int decimalScale) {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-commit-scale-" + decimalScale + "@example.com");
        var account = createAccount(ownerId, "Commit decimal scale " + decimalScale, "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "105"));
        var request = new ReconciliationCommitRequest(
                "scale-commit",
                times.openingAt(),
                times.closingAt(),
                "100",
                "105",
                UUID.randomUUID(),
                preview.projectionVersion(),
                ReconciliationAction.CREATE_ADJUSTMENT,
                "scale reason");
        var committed = reconciliationService.commit(ownerId, account.id(), request);
        var replay = new ReconciliationCommitRequest(
                request.statementReference(),
                request.statementOpeningAt(),
                request.statementClosingAt(),
                scaled("100", decimalScale),
                scaled("105", decimalScale),
                request.clientRequestId(),
                request.expectedBalanceVersion(),
                request.resolution(),
                request.adjustmentReason());

        assertThat(reconciliationService.commit(ownerId, account.id(), replay).id())
                .isEqualTo(committed.id());
    }

    @ParameterizedTest(name = "correction decimal scale {0}")
    @MethodSource("decimalScales")
    void correctionReplaysNumericallyEquivalentDecimalScales(int decimalScale) {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-correction-scale-" + decimalScale + "@example.com");
        var account = createAccount(ownerId, "Correction decimal scale " + decimalScale, "100", times.openingAt());
        var originalPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "105"));
        var original = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "scale-original",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "105",
                        UUID.randomUUID(),
                        originalPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "original reason"));
        var replacementPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "104"));
        var request = new ReconciliationCorrectionRequest(
                "scale-correction",
                times.openingAt(),
                times.closingAt(),
                "100",
                "104",
                UUID.randomUUID(),
                replacementPreview.projectionVersion(),
                ReconciliationAction.CREATE_ADJUSTMENT,
                "replacement reason",
                "correction reason");
        var replacement = reconciliationService.correct(ownerId, original.id(), request);
        var replay = new ReconciliationCorrectionRequest(
                request.statementReference(),
                request.statementOpeningAt(),
                request.statementClosingAt(),
                scaled("100", decimalScale),
                scaled("104", decimalScale),
                request.clientRequestId(),
                request.expectedBalanceVersion(),
                request.resolution(),
                request.adjustmentReason(),
                request.correctionReason());

        assertThat(reconciliationService.correct(ownerId, original.id(), replay).id())
                .isEqualTo(replacement.id());
    }

    @Test
    void negativeHistoricalAdjustmentCrossesHardFloorAndRecordsHistoricalBreach() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-negative-adjustment@example.com");
        var account = createAccount(ownerId, "Negative adjustment", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "-5"));

        var committed = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "negative-adjustment",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "-5",
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "Negative unexplained difference"));

        assertThat(committed.closingDifference()).isEqualTo("-105");
        assertThat(committed.adjustmentAmount()).isEqualTo("-105");
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("-5");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT amount FROM ledger.money_posting WHERE activity_id = ?",
                        java.math.BigDecimal.class,
                        committed.adjustmentActivityId()))
                .isEqualByComparingTo("-105");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT policy_decision FROM ledger.activity WHERE id = ?",
                        String.class,
                        committed.adjustmentActivityId()))
                .isEqualTo("HISTORICAL_BREACH_RECORDED");
    }

    private static Stream<String> commitFingerprintVariants() {
        return Stream.of(
                "account",
                "reference",
                "opening instant",
                "closing instant",
                "opening balance",
                "closing balance",
                "expected balance version",
                "resolution",
                "adjustment reason");
    }

    private static Stream<String> correctionFingerprintVariants() {
        return Stream.of(
                "target",
                "reference",
                "opening instant",
                "closing instant",
                "opening balance",
                "closing balance",
                "expected balance version",
                "resolution",
                "adjustment reason",
                "correction reason");
    }

    private static Stream<Integer> decimalScales() {
        return Stream.of(0, 1, 2);
    }

    private static String scaled(String integerValue, int decimalScale) {
        return decimalScale == 0 ? integerValue : integerValue + "." + "0".repeat(decimalScale);
    }

    @Test
    void correctionsCanFormAChainWhilePriorRowsRemainImmutable() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-chain@example.com");
        var account = createAccount(ownerId, "Correction chain", "100", times.openingAt());
        var firstPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "105"));
        var first = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "chain-1",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "105",
                        UUID.randomUUID(),
                        firstPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "first difference"));
        var secondPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "104"));
        var second = reconciliationService.correct(
                ownerId,
                first.id(),
                new ReconciliationCorrectionRequest(
                        "chain-2",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "104",
                        UUID.randomUUID(),
                        secondPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "second difference",
                        "correct second"));
        var thirdPreview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "103"));
        var third = reconciliationService.correct(
                ownerId,
                second.id(),
                new ReconciliationCorrectionRequest(
                        "chain-3",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "103",
                        UUID.randomUUID(),
                        thirdPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "third difference",
                        "correct third"));

        assertThat(reconciliationReadService.detail(ownerId, first.id()).lifecycleStatus())
                .hasToString("SUPERSEDED");
        assertThat(reconciliationReadService.detail(ownerId, second.id()).lifecycleStatus())
                .hasToString("SUPERSEDED");
        assertThat(third.lifecycleStatus()).hasToString("CURRENT");
        assertThat(count("ledger.reconciliation", ownerId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'REVERSAL'",
                        Integer.class,
                        ownerId))
                .isEqualTo(2);
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("103");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT adjustment_amount FROM ledger.reconciliation WHERE id = ?",
                        java.math.BigDecimal.class,
                        first.id()))
                .isEqualByComparingTo("5");
    }

    @Test
    void archivedAccountsSupportCommitAndCorrectionUsingTheirImmutableLedger() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-archived@example.com");
        var balanced = createAccount(ownerId, "Archived balanced", "100", times.openingAt());
        accountLifecycleService.archive(
                ownerId, balanced.id(), new ArchiveAccountRequest(UUID.randomUUID(), balanced.version()));
        var balancedPreview = reconciliationService.preview(ownerId, balanced.id(), previewRequest(times, "100"));
        assertThat(reconciliationService
                        .commit(
                                ownerId,
                                balanced.id(),
                                new ReconciliationCommitRequest(
                                        "archived-balanced",
                                        times.openingAt(),
                                        times.closingAt(),
                                        "100",
                                        "100",
                                        UUID.randomUUID(),
                                        balancedPreview.projectionVersion(),
                                        ReconciliationAction.CONFIRM_BALANCED,
                                        null))
                        .resolution())
                .hasToString("BALANCED");

        var adjusted = createAccount(ownerId, "Archived adjusted", "100", times.openingAt());
        var adjustedPreview = reconciliationService.preview(ownerId, adjusted.id(), previewRequest(times, "105"));
        var original = reconciliationService.commit(
                ownerId,
                adjusted.id(),
                new ReconciliationCommitRequest(
                        "archived-original",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "105",
                        UUID.randomUUID(),
                        adjustedPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "archived difference"));
        var archivedResponse = accountLifecycleService.archive(
                ownerId, adjusted.id(), new ArchiveAccountRequest(UUID.randomUUID(), adjusted.version()));
        var correctionPreview = reconciliationService.preview(ownerId, adjusted.id(), previewRequest(times, "104"));
        var replacement = reconciliationService.correct(
                ownerId,
                original.id(),
                new ReconciliationCorrectionRequest(
                        "archived-correction",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "104",
                        UUID.randomUUID(),
                        correctionPreview.projectionVersion(),
                        ReconciliationAction.CREATE_ADJUSTMENT,
                        "archived replacement",
                        "archived correction"));

        assertThat(archivedResponse.archived()).isTrue();
        assertThat(replacement.closingDifference()).isEqualTo("4");
        assertThat(accountQueryService.balance(ownerId, adjusted.id(), null).ledgerBalance())
                .isEqualTo("104");
    }

    @Test
    void laterRecordedBackdatedWithdrawalTransferReversalAndOpeningCorrectionMakeRowsStale() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-staleness-kinds@example.com");

        var withdrawalAccount = createAccount(ownerId, "Stale withdrawal", "100", times.openingAt());
        var withdrawalPreview =
                reconciliationService.preview(ownerId, withdrawalAccount.id(), previewRequest(times, "100"));
        var withdrawalReconciliation = reconciliationService.commit(
                ownerId,
                withdrawalAccount.id(),
                new ReconciliationCommitRequest(
                        "stale-withdrawal",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "100",
                        UUID.randomUUID(),
                        withdrawalPreview.projectionVersion(),
                        ReconciliationAction.CONFIRM_BALANCED,
                        null));
        activityService.recordCashActivity(
                ownerId,
                withdrawalAccount.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_WITHDRAWAL,
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));

        var transferSource = createAccount(ownerId, "Stale transfer source", "100", times.openingAt());
        var transferDestination = createAccount(ownerId, "Stale transfer destination", "0", times.openingAt());
        var transferPreview = transferService.preview(
                ownerId,
                new TransferPreviewRequest(
                        transferSource.id(),
                        transferDestination.id(),
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false));
        transferService.transfer(
                ownerId,
                new TransferRequest(
                        UUID.randomUUID(),
                        transferSource.id(),
                        transferDestination.id(),
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        transferPreview.sourceVersion(),
                        transferPreview.destinationVersion()));
        var transferReconciliation = commitBalanced(ownerId, transferSource.id(), times, "99", "stale-transfer");
        transferService.transfer(
                ownerId,
                new TransferRequest(
                        UUID.randomUUID(),
                        transferSource.id(),
                        transferDestination.id(),
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null,
                        null));

        var reversalAccount = createAccount(ownerId, "Stale reversal", "100", times.openingAt());
        var deposit = activityService.recordCashActivity(
                ownerId,
                reversalAccount.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "1",
                        RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));
        var reversalReconciliation = commitBalanced(ownerId, reversalAccount.id(), times, "101", "stale-reversal");
        activityService.reverse(ownerId, deposit.id(), new ReversalRequest(UUID.randomUUID(), "backdated reversal"));

        var openingAccount = createAccount(ownerId, "Stale opening", "100", times.openingAt());
        var openingReconciliation = commitBalanced(ownerId, openingAccount.id(), times, "100", "stale-opening");
        accountLifecycleService.correctOpening(
                ownerId,
                openingAccount.id(),
                new OpeningCorrectionRequest(
                        UUID.randomUUID(),
                        "101",
                        times.openingAt(),
                        "backdated opening correction",
                        openingAccount.version()));

        assertThat(reconciliationReadService
                        .detail(ownerId, withdrawalReconciliation.id())
                        .lifecycleStatus())
                .hasToString("STALE");
        assertThat(reconciliationReadService
                        .detail(ownerId, transferReconciliation.id())
                        .lifecycleStatus())
                .hasToString("STALE");
        assertThat(reconciliationReadService
                        .detail(ownerId, reversalReconciliation.id())
                        .lifecycleStatus())
                .hasToString("STALE");
        assertThat(reconciliationReadService
                        .detail(ownerId, openingReconciliation.id())
                        .lifecycleStatus())
                .hasToString("STALE");
    }

    @Test
    void lifecycleBecomesStaleOnlyForBackdatedPostingsAndOpeningMismatchCannotBeAdjustedAway() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-lifecycle@example.com");
        var account = createAccount(ownerId, "Lifecycle", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), previewRequest(times, "100"));
        var committed = reconciliationService.commit(
                ownerId,
                account.id(),
                new ReconciliationCommitRequest(
                        "lifecycle-statement",
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        "100",
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CONFIRM_BALANCED,
                        null));

        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "1",
                        dev.canverse.stocks.ledger.domain.RecordingMode.HISTORICAL_FACT,
                        times.closingAt().plusSeconds(1),
                        false,
                        null));
        assertThat(reconciliationReadService.detail(ownerId, committed.id()).lifecycleStatus())
                .hasToString("CURRENT");

        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "1",
                        dev.canverse.stocks.ledger.domain.RecordingMode.HISTORICAL_FACT,
                        times.depositAt(),
                        false,
                        null));
        assertThat(reconciliationReadService.detail(ownerId, committed.id()).lifecycleStatus())
                .hasToString("STALE");

        var mismatch = new ReconciliationPreviewRequest("mismatch", times.openingAt(), times.closingAt(), "99", "102");
        assertThatThrownBy(() -> reconciliationService.commit(
                        ownerId,
                        account.id(),
                        new ReconciliationCommitRequest(
                                mismatch.statementReference(),
                                mismatch.statementOpeningAt(),
                                mismatch.statementClosingAt(),
                                mismatch.statementOpeningBalance(),
                                mismatch.statementClosingBalance(),
                                UUID.randomUUID(),
                                reconciliationService
                                        .preview(ownerId, account.id(), mismatch)
                                        .projectionVersion(),
                                ReconciliationAction.CREATE_ADJUSTMENT,
                                "Must not hide opening gap")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.RECONCILIATION_OPENING_MISMATCH);
    }

    @Test
    void liabilitySignsAreNotFlippedAndHoldingsOnlyAccountsAreRejected() {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-capability@example.com");
        var liability = accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        "Liability",
                        AccountKind.CREDIT_CARD,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        null,
                        null,
                        new dev.canverse.stocks.ledger.web.request.OpeningStateRequest("100", times.openingAt())));
        var liabilityPreview = reconciliationService.preview(ownerId, liability.id(), previewRequest(times, "100"));
        assertThat(liabilityPreview.ledgerOpeningBalance()).isEqualTo("100");
        assertThat(liabilityPreview.ledgerClosingBalanceBeforeAdjustment()).isEqualTo("100");
        assertThat(liabilityPreview.closingDifference()).isEqualTo("0");
        var beforeCoverage = new ReconciliationPreviewRequest(
                "before coverage", times.openingAt().minusSeconds(1), times.closingAt(), "0", "100");
        assertThatThrownBy(() -> reconciliationService.preview(ownerId, liability.id(), beforeCoverage))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.RECONCILIATION_COVERAGE_GAP);

        var holdingsOnly = accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        "Holdings only",
                        AccountKind.BROKERAGE,
                        TrackingMode.HOLDINGS_ONLY,
                        "USD",
                        "UTC",
                        null,
                        null,
                        null));
        assertThatThrownBy(() -> reconciliationService.preview(ownerId, holdingsOnly.id(), previewRequest(times, "0")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
    }

    private dev.canverse.stocks.ledger.web.response.ReconciliationResponse commitBalanced(
            UUID ownerId, UUID accountId, Times times, String closingBalance, String reference) {
        var preview = reconciliationService.preview(ownerId, accountId, previewRequest(times, closingBalance));
        return reconciliationService.commit(
                ownerId,
                accountId,
                new ReconciliationCommitRequest(
                        reference,
                        times.openingAt(),
                        times.closingAt(),
                        "100",
                        closingBalance,
                        UUID.randomUUID(),
                        preview.projectionVersion(),
                        ReconciliationAction.CONFIRM_BALANCED,
                        null));
    }

    private ReconciliationPreviewRequest previewRequest(Times times, String closingBalance) {
        return new ReconciliationPreviewRequest(
                "statement-" + UUID.randomUUID(), times.openingAt(), times.closingAt(), "100.00", closingBalance);
    }

    private FinancialAccountResponse createAccount(UUID ownerId, String name, String openingAmount, Instant openingAt) {
        return accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        name,
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        NegativeBalancePolicy.HARD_FLOOR,
                        null,
                        new dev.canverse.stocks.ledger.web.request.OpeningStateRequest(openingAmount, openingAt)));
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                email,
                now,
                now);
        return id;
    }

    private int count(String table, UUID ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE owner_user_account_id = ?".formatted(table), Integer.class, ownerId);
    }

    private record Times(Instant openingAt, Instant depositAt, Instant closingAt) {
        static Times create() {
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            return new Times(now.minusSeconds(300), now.minusSeconds(200), now.minusSeconds(100));
        }
    }
}
