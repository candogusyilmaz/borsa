package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.ReconciliationCommandService;
import dev.canverse.stocks.ledger.application.ReconciliationReadService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationAction;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
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
class LedgerReconciliationConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    ReconciliationCommandService reconciliationService;

    @Autowired
    ReconciliationReadService reconciliationReadService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void concurrentAdjustmentCommitsSerializeAndOnlyOneVersionedWinnerPostsMoney() throws Exception {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-concurrent-commit@example.com");
        var account = createAccount(ownerId, "Concurrent reconciliation", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), preview(times, "105"));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCommit(start, ownerId, account.id(), preview.projectionVersion(), UUID.randomUUID(), "concurrent one"));
            var second = executor.submit(() -> runCommit(start, ownerId, account.id(), preview.projectionVersion(), UUID.randomUUID(), "concurrent two"));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(java.util.Objects::nonNull)).containsExactly(LedgerErrorCode.BALANCE_VERSION_CONFLICT);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'RECONCILIATION_ADJUSTMENT'", Integer.class,
                    ownerId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateConcurrentRetriesReplayOneEconomicResult() throws Exception {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-concurrent-retry@example.com");
        var account = createAccount(ownerId, "Concurrent retry", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), preview(times, "105"));
        var requestId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCommit(start, ownerId, account.id(), preview.projectionVersion(), requestId, "same request"));
            var second = executor.submit(() -> runCommit(start, ownerId, account.id(), preview.projectionVersion(), requestId, "same request"));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes).extracting(Outcome::id).containsExactly(outcomes.getFirst().id(), outcomes.getFirst().id());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ? AND posting_role = 'ADJUSTMENT'",
                    Integer.class, ownerId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateConcurrentCorrectionRetriesReplayOneReplacement() throws Exception {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-concurrent-correction-retry@example.com");
        var account = createAccount(ownerId, "Concurrent correction retry", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), preview(times, "105"));
        var original = reconciliationService.commit(ownerId, account.id(),
                new ReconciliationCommitRequest("original retry", times.openingAt(), times.closingAt(), "100", "105", UUID.randomUUID(),
                        preview.projectionVersion(), ReconciliationAction.CREATE_ADJUSTMENT, "Original difference"));
        var replacementPreview = reconciliationService.preview(ownerId, account.id(), preview(times, "104"));
        var requestId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCorrection(start, ownerId, original.id(), replacementPreview.projectionVersion(), requestId));
            var second = executor.submit(() -> runCorrection(start, ownerId, original.id(), replacementPreview.projectionVersion(), requestId));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes).extracting(Outcome::id).containsOnly(outcomes.getFirst().id());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'REVERSAL'",
                    Integer.class, ownerId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCorrectionsAllowOnlyOneDirectSuperseder() throws Exception {
        var times = Times.create();
        var ownerId = insertUser("reconciliation-concurrent-correction@example.com");
        var account = createAccount(ownerId, "Concurrent correction", "100", times.openingAt());
        var preview = reconciliationService.preview(ownerId, account.id(), preview(times, "105"));
        var original = reconciliationService.commit(ownerId, account.id(), new ReconciliationCommitRequest("original", times.openingAt(), times.closingAt(),
                "100", "105", UUID.randomUUID(), preview.projectionVersion(), ReconciliationAction.CREATE_ADJUSTMENT, "Original difference"));
        var replacementPreview = reconciliationService.preview(ownerId, account.id(), preview(times, "104"));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runCorrection(start, ownerId, original.id(), replacementPreview.projectionVersion(), UUID.randomUUID()));
            var second = executor.submit(() -> runCorrection(start, ownerId, original.id(), replacementPreview.projectionVersion(), UUID.randomUUID()));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().map(Outcome::errorCode).filter(java.util.Objects::nonNull))
                    .containsExactly(LedgerErrorCode.RECONCILIATION_ALREADY_SUPERSEDED);
            assertThat(reconciliationReadService.detail(ownerId, original.id()).lifecycleStatus()).hasToString("SUPERSEDED");
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome runCommit(CountDownLatch start, UUID ownerId, UUID accountId, long version, UUID requestId, String reason) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager)
                    .execute(status -> reconciliationService.commit(ownerId, accountId, new ReconciliationCommitRequest("concurrent", Times.BASE_OPENING,
                            Times.BASE_CLOSING, "100", "105", requestId, version, ReconciliationAction.CREATE_ADJUSTMENT, reason)));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private Outcome runCorrection(CountDownLatch start, UUID ownerId, UUID reconciliationId, long version, UUID requestId) {
        await(start);
        try {
            var response = new TransactionTemplate(transactionManager).execute(status -> reconciliationService.correct(ownerId, reconciliationId,
                    new ReconciliationCorrectionRequest("corrected", Times.BASE_OPENING, Times.BASE_CLOSING, "100", "104", requestId, version,
                            ReconciliationAction.CREATE_ADJUSTMENT, "Replacement difference", "Concurrent correction")));
            return Outcome.success(response.id());
        } catch (Throwable exception) {
            return Outcome.failure(errorCode(exception));
        }
    }

    private FinancialAccountResponse createAccount(UUID ownerId, String name, String amount, Instant openingAt) {
        return accountService.create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), name, AccountKind.CASH_CURRENT, TrackingMode.FULL_LEDGER,
                "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new dev.canverse.stocks.ledger.web.request.OpeningStateRequest(amount, openingAt)));
    }

    private ReconciliationPreviewRequest preview(Times times, String closingBalance) {
        return new ReconciliationPreviewRequest("preview", times.openingAt(), times.closingAt(), "100", closingBalance);
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, email, email, now, now));
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
            if (cause instanceof dev.canverse.stocks.platform.error.AppException appException &&
                    appException.getErrorCode() instanceof LedgerErrorCode ledgerErrorCode) {
                return ledgerErrorCode;
            }
        }
        return null;
    }

    private record Outcome(UUID id, LedgerErrorCode errorCode) {
        static Outcome success(UUID id) {
            return new Outcome(id, null);
        }

        static Outcome failure(LedgerErrorCode errorCode) {
            return new Outcome(null, errorCode);
        }

        boolean succeeded() {
            return id != null;
        }
    }

    private record Times(Instant openingAt, Instant closingAt) {
        static final Instant BASE_OPENING = Instant.parse("2026-08-23T15:00:00Z");
        static final Instant BASE_CLOSING = Instant.parse("2026-08-23T15:30:00Z");

        static Times create() {
            return new Times(BASE_OPENING, BASE_CLOSING);
        }
    }
}
