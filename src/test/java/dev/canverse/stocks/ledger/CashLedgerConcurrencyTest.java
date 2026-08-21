package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.CashTransferService;
import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
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
import dev.canverse.stocks.ledger.web.request.OpeningCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CashLedgerConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountLifecycleService lifecycleService;

    @Autowired
    FinancialAccountQueryService queryService;

    @Autowired
    CashActivityCommandService activityService;

    @Autowired
    CashTransferService transferService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void concurrentHardFloorWithdrawalsHaveOneSeriallyValidWinner() throws Exception {
        var ownerId = insertUser("ledger-concurrent-withdrawal@example.com");
        var account = createAccount(ownerId, "Concurrent withdrawal", "100");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCashCommand(start, ownerId, account.id(), "75"));
            var second = executor.submit(() -> runCashCommand(start, ownerId, account.id(), "75"));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(java.util.Objects::nonNull))
                    .containsExactly(LedgerErrorCode.INSUFFICIENT_FUNDS);
            assertThat(queryService.balance(ownerId, account.id(), null).ledgerBalance())
                    .isEqualTo("25");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAuthorizedLimitWithdrawalsHaveOneBoundaryWinnerWithoutOverspend() throws Exception {
        var ownerId = insertUser("ledger-concurrent-authorized-limit@example.com");
        var account = createAccount(
                ownerId, "Concurrent authorized limit", "0", NegativeBalancePolicy.AUTHORIZED_LIMIT, "50");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCashCommand(start, ownerId, account.id(), "50"));
            var second = executor.submit(() -> runCashCommand(start, ownerId, account.id(), "50"));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(java.util.Objects::nonNull))
                    .containsExactly(LedgerErrorCode.ACCOUNT_LIMIT_EXCEEDED);
            assertThat(queryService.balance(ownerId, account.id(), null).ledgerBalance())
                    .isEqualTo("-50");
            assertThat(queryService.balance(ownerId, account.id(), null).creditAvailable())
                    .isEqualTo("0");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAccountCreationReplayCreatesOneAccountAndOneOpeningWorkflow() throws Exception {
        var ownerId = insertUser("ledger-concurrent-account-create@example.com");
        var request = new CreateFinancialAccountRequest(
                UUID.randomUUID(),
                "Concurrent account",
                AccountKind.CASH_CURRENT,
                TrackingMode.FULL_LEDGER,
                "USD",
                "UTC",
                NegativeBalancePolicy.HARD_FLOOR,
                null,
                new dev.canverse.stocks.ledger.web.request.OpeningStateRequest(
                        "25", Instant.now().minusSeconds(10)));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runAccountCreate(start, ownerId, request));
            var second = executor.submit(() -> runAccountCreate(start, ownerId, request));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::activityId).distinct()).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.financial_account WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsOneActivityToBothCallers() throws Exception {
        var ownerId = insertUser("ledger-concurrent-retry@example.com");
        var account = createAccount(ownerId, "Concurrent retry", "100");
        var request = new CashActivityRequest(
                UUID.randomUUID(),
                ActivityType.CASH_DEPOSIT,
                "25.00",
                RecordingMode.CURRENT_ACTION,
                Instant.now().minusSeconds(1),
                false,
                null);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runSameRequest(start, ownerId, account.id(), request));
            var second = executor.submit(() -> runSameRequest(start, ownerId, account.id(), request));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::activityId).distinct()).hasSize(1);
            assertThat(queryService.balance(ownerId, account.id(), null).ledgerBalance())
                    .isEqualTo("125");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oppositeTransfersUseDeterministicAccountLockOrder() throws Exception {
        var ownerId = insertUser("ledger-concurrent-transfer@example.com");
        var firstAccount = createAccount(ownerId, "First transfer", "100");
        var secondAccount = createAccount(ownerId, "Second transfer", "100");
        var firstRequest = transferRequest(firstAccount.id(), secondAccount.id());
        var secondRequest = transferRequest(secondAccount.id(), firstAccount.id());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runTransfer(start, ownerId, firstRequest));
            var second = executor.submit(() -> runTransfer(start, ownerId, secondRequest));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(java.util.Objects::nonNull))
                    .isEmpty();
            assertThat(queryService.balance(ownerId, firstAccount.id(), null).ledgerBalance())
                    .isEqualTo("100");
            assertThat(queryService.balance(ownerId, secondAccount.id(), null).ledgerBalance())
                    .isEqualTo("100");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameTransferKeyReturnsOneActivityToBothCallers() throws Exception {
        var ownerId = insertUser("ledger-concurrent-transfer-retry@example.com");
        var source = createAccount(ownerId, "Transfer retry source", "100");
        var destination = createAccount(ownerId, "Transfer retry destination", "10");
        var request = transferRequest(source.id(), destination.id());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runTransfer(start, ownerId, request));
            var second = executor.submit(() -> runTransfer(start, ownerId, request));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::activityId).distinct()).hasSize(1);
            assertThat(queryService.balance(ownerId, source.id(), null).ledgerBalance())
                    .isEqualTo("90");
            assertThat(queryService.balance(ownerId, destination.id(), null).ledgerBalance())
                    .isEqualTo("20");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameReversalKeyReturnsOneReversalToBothCallers() throws Exception {
        var ownerId = insertUser("ledger-concurrent-reversal-retry@example.com");
        var account = createAccount(ownerId, "Reversal retry", "100");
        var original = activityService.recordCashActivity(
                ownerId,
                account.id(),
                new CashActivityRequest(
                        UUID.randomUUID(),
                        ActivityType.CASH_DEPOSIT,
                        "10",
                        RecordingMode.CURRENT_ACTION,
                        Instant.now().minusSeconds(1),
                        false,
                        null));
        var request = new ReversalRequest(UUID.randomUUID(), "Concurrent reversal");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runReversal(start, ownerId, original.id(), request));
            var second = executor.submit(() -> runReversal(start, ownerId, original.id(), request));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::activityId).distinct()).hasSize(1);
            assertThat(queryService.balance(ownerId, account.id(), null).ledgerBalance())
                    .isEqualTo("100");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameOpeningCorrectionKeyReturnsOneCorrectionToBothCallers() throws Exception {
        var ownerId = insertUser("ledger-concurrent-opening-retry@example.com");
        var account = createAccount(ownerId, "Opening retry", "100");
        var effectiveAt = jdbcTemplate
                .queryForObject(
                        "SELECT effective_at FROM ledger.activity WHERE id = (SELECT current_opening_activity_id FROM ledger.financial_account WHERE id = ?)",
                        OffsetDateTime.class,
                        account.id())
                .toInstant();
        var request = new OpeningCorrectionRequest(
                UUID.randomUUID(), "125", effectiveAt, "Concurrent opening correction", account.version());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runOpeningCorrection(start, ownerId, account.id(), request));
            var second = executor.submit(() -> runOpeningCorrection(start, ownerId, account.id(), request));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().map(Outcome::activityId).distinct()).hasSize(1);
            assertThat(queryService.balance(ownerId, account.id(), null).ledgerBalance())
                    .isEqualTo("125");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ?",
                            Integer.class,
                            ownerId))
                    .isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome runCashCommand(CountDownLatch start, UUID ownerId, UUID accountId, String amount) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> activityService.recordCashActivity(
                            ownerId,
                            accountId,
                            new CashActivityRequest(
                                    UUID.randomUUID(),
                                    ActivityType.CASH_WITHDRAWAL,
                                    amount,
                                    RecordingMode.CURRENT_ACTION,
                                    Instant.now().minusSeconds(1),
                                    false,
                                    null)));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runSameRequest(CountDownLatch start, UUID ownerId, UUID accountId, CashActivityRequest request) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> activityService.recordCashActivity(ownerId, accountId, request));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runAccountCreate(CountDownLatch start, UUID ownerId, CreateFinancialAccountRequest request) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> accountService.create(ownerId, request));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runTransfer(CountDownLatch start, UUID ownerId, TransferRequest request) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> transferService.transfer(ownerId, request));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runReversal(CountDownLatch start, UUID ownerId, UUID activityId, ReversalRequest request) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> activityService.reverse(ownerId, activityId, request));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runOpeningCorrection(
            CountDownLatch start, UUID ownerId, UUID accountId, OpeningCorrectionRequest request) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> lifecycleService.correctOpening(ownerId, accountId, request));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private static TransferRequest transferRequest(UUID sourceAccountId, UUID destinationAccountId) {
        return new TransferRequest(
                UUID.randomUUID(),
                sourceAccountId,
                destinationAccountId,
                "10",
                RecordingMode.CURRENT_ACTION,
                Instant.now().minusSeconds(1),
                false,
                null,
                null);
    }

    private FinancialAccountResponse createAccount(UUID ownerId, String name, String openingAmount) {
        return createAccount(ownerId, name, openingAmount, NegativeBalancePolicy.HARD_FLOOR, null);
    }

    private FinancialAccountResponse createAccount(
            UUID ownerId, String name, String openingAmount, NegativeBalancePolicy policy, String authorizedLimit) {
        return accountService.create(
                ownerId,
                new CreateFinancialAccountRequest(
                        UUID.randomUUID(),
                        name,
                        AccountKind.CASH_CURRENT,
                        TrackingMode.FULL_LEDGER,
                        "USD",
                        "UTC",
                        policy,
                        authorizedLimit,
                        new dev.canverse.stocks.ledger.web.request.OpeningStateRequest(
                                openingAmount, Instant.now().minusSeconds(10))));
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbcTemplate.update(
                        "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                        id,
                        email,
                        email,
                        now,
                        now));
        return id;
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", exception);
        }
    }

    private static LedgerErrorCode errorCode(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof dev.canverse.stocks.platform.error.AppException appException
                    && appException.getErrorCode() instanceof LedgerErrorCode ledgerErrorCode) {
                return ledgerErrorCode;
            }
        }
        return null;
    }

    private record Outcome(UUID activityId, LedgerErrorCode errorCode) {
        static Outcome success(UUID activityId) {
            return new Outcome(activityId, null);
        }

        static Outcome failure(LedgerErrorCode errorCode) {
            return new Outcome(null, errorCode);
        }

        boolean succeeded() {
            return activityId != null;
        }
    }
}
