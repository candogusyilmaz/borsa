package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.CashActivityQueryService;
import dev.canverse.stocks.ledger.application.CashTransferService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.ledger.web.request.TransferPreviewRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class CashActivityServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountQueryService accountQueryService;

    @Autowired
    CashActivityCommandService activityService;

    @Autowired
    CashActivityQueryService activityQueryService;

    @Autowired
    CashTransferService transferService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void depositWithdrawalAndExactRetryProduceSignedImmutableFacts() {
        var ownerId = insertUser("cash-facts-owner@example.com");
        var account = createAccount(ownerId, "Cash facts", NegativeBalancePolicy.HARD_FLOOR, "100");
        var depositRequest =
                cashRequest(ActivityType.CASH_DEPOSIT, "25.500", RecordingMode.CURRENT_ACTION, false, null);
        var deposited = activityService.recordCashActivity(ownerId, account.id(), depositRequest);
        var replay = activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        depositRequest.clientRequestId(),
                        ActivityType.CASH_DEPOSIT,
                        "25.5",
                        RecordingMode.CURRENT_ACTION,
                        depositRequest.effectiveAt(),
                        false,
                        null));
        var withdrawn = activityService.recordCashActivity(
                ownerId,
                account.id(),
                cashRequest(ActivityType.CASH_WITHDRAWAL, "20", RecordingMode.CURRENT_ACTION, false, null));

        assertThat(replay.id()).isEqualTo(deposited.id());
        assertThat(deposited.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.amount()).isEqualTo("25.5");
            assertThat(posting.role().name()).isEqualTo("DEPOSIT");
        });
        assertThat(withdrawn.postings()).singleElement().satisfies(posting -> {
            assertThat(posting.amount()).isEqualTo("-20");
            assertThat(posting.role().name()).isEqualTo("WITHDRAWAL");
        });
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("105.5");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isEqualTo(3);

        var conflictingRequest = new CashActivityRequest(
                depositRequest.clientRequestId(),
                ActivityType.CASH_DEPOSIT,
                "26",
                RecordingMode.CURRENT_ACTION,
                deposited.effectiveAt(),
                false,
                null);
        assertThatThrownBy(() -> activityService.recordCashActivity(ownerId, account.id(), conflictingRequest))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void negativePoliciesAreAppliedOnlyToCurrentActions() {
        var ownerId = insertUser("cash-policy-owner@example.com");
        var hard = createAccount(ownerId, "Hard floor", NegativeBalancePolicy.HARD_FLOOR, "0");
        var hardFailure = cashRequest(ActivityType.CASH_WITHDRAWAL, "1", RecordingMode.CURRENT_ACTION, false, null);
        assertThatThrownBy(() -> activityService.recordCashActivity(ownerId, hard.id(), hardFailure))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.INSUFFICIENT_FUNDS);

        var soft = createAccount(ownerId, "Soft floor", NegativeBalancePolicy.SOFT_FLOOR, "0");
        var softRequest = cashRequest(ActivityType.CASH_WITHDRAWAL, "1", RecordingMode.CURRENT_ACTION, false, null);
        assertThatThrownBy(() -> activityService.recordCashActivity(ownerId, soft.id(), softRequest))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.POLICY_BREACH_CONFIRMATION_REQUIRED);
        var confirmed = activityService.recordCashActivity(
                ownerId,
                soft.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_WITHDRAWAL,
                        "1",
                        RecordingMode.CURRENT_ACTION,
                        Instant.now().minusSeconds(1),
                        true,
                        null));
        assertThat(confirmed.policyDecision().name()).isEqualTo("CONFIRMED_BREACH");

        var reality = createAccount(ownerId, "Historical reality", NegativeBalancePolicy.TRACK_REALITY, "0");
        var historical = activityService.recordCashActivity(
                ownerId,
                reality.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_WITHDRAWAL,
                        "5",
                        RecordingMode.HISTORICAL_FACT,
                        Instant.now().minusSeconds(2),
                        false,
                        null));
        assertThat(historical.policyDecision().name()).isEqualTo("HISTORICAL_BREACH_RECORDED");
        assertThat(accountQueryService.balance(ownerId, reality.id(), null).overdraftUsed())
                .isEqualTo("5");
    }

    @Test
    void authorizedLimitAndLiabilityCapabilityBoundariesAreExplicit() {
        var ownerId = insertUser("cash-capability-owner@example.com");
        var authorized = createAccount(
                ownerId, "Authorized", NegativeBalancePolicy.AUTHORIZED_LIMIT, "0", AccountKind.CASH_CURRENT);
        var allowed = activityService.recordCashActivity(
                ownerId,
                authorized.id(),
                cashRequest(ActivityType.CASH_WITHDRAWAL, "50", RecordingMode.CURRENT_ACTION, false, null));
        assertThat(allowed.postings())
                .singleElement()
                .extracting(posting -> posting.amount())
                .isEqualTo("-50");
        assertThat(accountQueryService.balance(ownerId, authorized.id(), null).creditAvailable())
                .isEqualTo("0");
        assertThatThrownBy(() -> activityService.recordCashActivity(
                        ownerId,
                        authorized.id(),
                        cashRequest(ActivityType.CASH_WITHDRAWAL, "0.01", RecordingMode.CURRENT_ACTION, false, null)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_LIMIT_EXCEEDED);

        var liability = createAccount(ownerId, "Card", null, "100", AccountKind.CREDIT_CARD);
        var liabilityBalance = accountQueryService.balance(ownerId, liability.id(), null);
        assertThat(liabilityBalance.liabilityOutstanding()).isEqualTo("100");
        assertThatThrownBy(() -> activityService.recordCashActivity(
                        ownerId,
                        liability.id(),
                        cashRequest(ActivityType.CASH_DEPOSIT, "1", RecordingMode.CURRENT_ACTION, false, null)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
    }

    @Test
    void reversalAppendsInversePostingAndOpeningCannotUseGenericReversal() {
        var ownerId = insertUser("cash-reversal-owner@example.com");
        var account = createAccount(ownerId, "Reversible", NegativeBalancePolicy.HARD_FLOOR, "10");
        var deposit = activityService.recordCashActivity(
                ownerId,
                account.id(),
                cashRequest(ActivityType.CASH_DEPOSIT, "5", RecordingMode.CURRENT_ACTION, false, null));
        var reversal = activityService.reverse(
                ownerId, deposit.id(), new ReversalRequest(UUID.randomUUID(), "Duplicate deposit"));

        assertThat(reversal.activityType().name()).isEqualTo("REVERSAL");
        assertThat(reversal.reversesActivityId()).isEqualTo(deposit.id());
        assertThat(reversal.postings())
                .singleElement()
                .extracting(posting -> posting.amount())
                .isEqualTo("-5");
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("10");
        assertThatThrownBy(() -> activityService.reverse(
                        ownerId, deposit.id(), new ReversalRequest(UUID.randomUUID(), "Second reversal")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACTIVITY_ALREADY_REVERSED);
        var openingId = jdbcTemplate.queryForObject(
                "SELECT current_opening_activity_id FROM ledger.financial_account WHERE id = ?",
                UUID.class,
                account.id());
        assertThatThrownBy(() -> activityService.reverse(
                        ownerId, openingId, new ReversalRequest(UUID.randomUUID(), "Opening reversal")))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
    }

    @Test
    void futureCashActivityUsesCapabilityError() {
        var ownerId = insertUser("cash-future-activity-owner@example.com");
        var account = createAccount(ownerId, "Future activity", NegativeBalancePolicy.HARD_FLOOR, "10");

        assertThatThrownBy(() -> activityService.recordCashActivity(
                        ownerId,
                        account.id(),
                        new CashActivityRequest(
                                UUID.randomUUID(),
                                ActivityType.CASH_DEPOSIT,
                                "1",
                                RecordingMode.CURRENT_ACTION,
                                Instant.now().plusSeconds(60),
                                false,
                                null)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
    }

    @Test
    void futureTransferPreviewAndCommitUseCapabilityError() {
        var ownerId = insertUser("cash-future-transfer-owner@example.com");
        var source = createAccount(ownerId, "Future source", NegativeBalancePolicy.HARD_FLOOR, "10");
        var destination = createAccount(ownerId, "Future destination", NegativeBalancePolicy.HARD_FLOOR, "10");
        var futureAt = Instant.now().plusSeconds(60);

        assertThatThrownBy(() -> transferService.preview(
                        ownerId,
                        new TransferPreviewRequest(
                                source.id(), destination.id(), "1", RecordingMode.CURRENT_ACTION, futureAt, false)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);

        assertThatThrownBy(() -> transferService.transfer(
                        ownerId,
                        new TransferRequest(
                                UUID.randomUUID(),
                                source.id(),
                                destination.id(),
                                "1",
                                RecordingMode.CURRENT_ACTION,
                                futureAt,
                                false,
                                null,
                                null)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
    }

    @Test
    void transferPreviewAndCommitUseEqualOppositeNativeCurrencyPostings() {
        var ownerId = insertUser("cash-transfer-owner@example.com");
        var source = createAccount(ownerId, "Source", NegativeBalancePolicy.HARD_FLOOR, "100");
        var destination = createAccount(ownerId, "Destination", NegativeBalancePolicy.HARD_FLOOR, "10");
        var effectiveAt = Instant.now().minusSeconds(1);
        var preview = transferService.preview(
                ownerId,
                new TransferPreviewRequest(
                        source.id(), destination.id(), "25.00", RecordingMode.CURRENT_ACTION, effectiveAt, false));
        assertThat(preview.sourceBefore()).isEqualTo("100");
        assertThat(preview.sourceAfter()).isEqualTo("75");
        assertThat(preview.destinationBefore()).isEqualTo("10");
        assertThat(preview.destinationAfter()).isEqualTo("35");
        assertThat(preview.allowed()).isTrue();

        var transfer = transferService.transfer(
                ownerId,
                new TransferRequest(
                        UUID.randomUUID(),
                        source.id(),
                        destination.id(),
                        "25.00",
                        RecordingMode.CURRENT_ACTION,
                        effectiveAt,
                        false,
                        preview.sourceVersion(),
                        preview.destinationVersion()));
        assertThat(transfer.postings()).hasSize(2);
        assertThat(transfer.postings()).extracting(posting -> posting.amount()).containsExactlyInAnyOrder("-25", "25");
        assertThat(accountQueryService.balance(ownerId, source.id(), null).ledgerBalance())
                .isEqualTo("75");
        assertThat(accountQueryService.balance(ownerId, destination.id(), null).ledgerBalance())
                .isEqualTo("35");
    }

    @Test
    void transferDestinationPolicyStillRequiresConfirmationWhenAlreadyOverdrawn() {
        var ownerId = insertUser("cash-transfer-policy-owner@example.com");
        var source = createAccount(ownerId, "Policy source", NegativeBalancePolicy.HARD_FLOOR, "100");
        var destination = createAccount(ownerId, "Policy destination", NegativeBalancePolicy.SOFT_FLOOR, "-10");
        var request = new TransferPreviewRequest(
                source.id(),
                destination.id(),
                "1",
                RecordingMode.CURRENT_ACTION,
                Instant.now().minusSeconds(1),
                false);

        var preview = transferService.preview(ownerId, request);
        assertThat(preview.allowed()).isFalse();
        assertThat(preview.destinationDecision().name()).isEqualTo("NOT_APPLICABLE");
        assertThatThrownBy(() -> transferService.transfer(
                        ownerId,
                        new TransferRequest(
                                UUID.randomUUID(),
                                source.id(),
                                destination.id(),
                                "1",
                                RecordingMode.CURRENT_ACTION,
                                request.effectiveAt(),
                                false,
                                null,
                                null)))
                .extracting(exception -> ((dev.canverse.stocks.platform.error.AppException) exception).getErrorCode())
                .isEqualTo(LedgerErrorCode.POLICY_BREACH_CONFIRMATION_REQUIRED);

        var confirmed = transferService.transfer(
                ownerId,
                new TransferRequest(
                        UUID.randomUUID(),
                        source.id(),
                        destination.id(),
                        "1",
                        RecordingMode.CURRENT_ACTION,
                        request.effectiveAt(),
                        true,
                        null,
                        null));
        assertThat(confirmed.postings()).hasSize(2);
        assertThat(confirmed.policyDecision().name()).isEqualTo("CONFIRMED_BREACH");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT policy_decision FROM ledger.activity WHERE id = ?", String.class, confirmed.id()))
                .isEqualTo("CONFIRMED_BREACH");
        assertThat(accountQueryService.balance(ownerId, destination.id(), null).ledgerBalance())
                .isEqualTo("-9");
    }

    @Test
    void historicalAsOfReadsPostingsInsteadOfTheCurrentProjection() {
        var ownerId = insertUser("cash-history-owner@example.com");
        var openingAt = Instant.now().minusSeconds(20).truncatedTo(ChronoUnit.MICROS);
        var account = createAccount(ownerId, "History", NegativeBalancePolicy.HARD_FLOOR, "100", openingAt);
        var depositAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS);
        activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "25",
                        RecordingMode.CURRENT_ACTION,
                        depositAt,
                        false,
                        null));

        assertThat(accountQueryService.balance(ownerId, account.id(), openingAt).ledgerBalance())
                .isEqualTo("100");
        assertThat(accountQueryService.balance(ownerId, account.id(), null).ledgerBalance())
                .isEqualTo("125");
    }

    private FinancialAccountResponse createAccount(
            UUID ownerId, String name, NegativeBalancePolicy policy, String openingAmount) {
        return createAccount(ownerId, name, policy, openingAmount, AccountKind.CASH_CURRENT);
    }

    private FinancialAccountResponse createAccount(
            UUID ownerId, String name, NegativeBalancePolicy policy, String openingAmount, AccountKind kind) {
        return createAccount(
                ownerId, name, policy, openingAmount, kind, Instant.now().minusSeconds(10));
    }

    private FinancialAccountResponse createAccount(
            UUID ownerId, String name, NegativeBalancePolicy policy, String openingAmount, Instant openingAt) {
        return createAccount(ownerId, name, policy, openingAmount, AccountKind.CASH_CURRENT, openingAt);
    }

    private FinancialAccountResponse createAccount(
            UUID ownerId,
            String name,
            NegativeBalancePolicy policy,
            String openingAmount,
            AccountKind kind,
            Instant openingAt) {
        return accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        name,
                        kind,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        policy,
                        policy == NegativeBalancePolicy.AUTHORIZED_LIMIT ? "50" : null,
                        new dev.canverse.stocks.ledger.web.request.OpeningStateRequest(openingAmount, openingAt)));
    }

    private CashActivityRequest cashRequest(
            ActivityType activityType,
            String amount,
            RecordingMode recordingMode,
            boolean confirmPolicyBreach,
            Long expectedVersion) {

        return new CashActivityRequest(
                UUID.randomUUID(),
                activityType,
                amount,
                recordingMode,
                Instant.now().minusSeconds(1),
                confirmPolicyBreach,
                expectedVersion);
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
}
