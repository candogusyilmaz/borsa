package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.ReconciliationCommandService;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.IdempotencyRecord;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.Reconciliation;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.IdempotencyRecordRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationRepository;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationAction;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerTransactionRollbackTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    ReconciliationCommandService reconciliationCommandService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    ActivityRepository activityRepository;

    @MockitoSpyBean
    MoneyPostingRepository moneyPostingRepository;

    @MockitoSpyBean
    AccountBalanceProjectionRepository projectionRepository;

    @MockitoSpyBean
    ReconciliationRepository reconciliationRepository;

    @MockitoSpyBean
    IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void cleanDatabase() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate
                .execute("TRUNCATE TABLE ledger.reconciliation, ledger.money_posting, ledger.activity, ledger.account_balance_projection," +
                        " ledger.account_cash_pocket, ledger.idempotency_record, ledger.financial_account," + " identity.user_account CASCADE"));
        reset(activityRepository, moneyPostingRepository, projectionRepository, idempotencyRecordRepository, reconciliationRepository);
    }

    @Test
    void postingFailureRollsBackTheEntireOnboardingWorkflow() {
        var ownerId = insertUser("posting-failure");
        doThrow(new DataIntegrityViolationException("posting failure")).when(moneyPostingRepository).save(any(MoneyPosting.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request())).isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void projectionFailureRollsBackFactsAndAccountState() {
        var ownerId = insertUser("projection-failure");
        doThrow(new DataIntegrityViolationException("projection failure")).when(projectionRepository).save(any(AccountBalanceProjection.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request())).isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void idempotencyFailureRollsBackFactsAndAccountState() {
        var ownerId = insertUser("idempotency-failure");
        doThrow(new DataIntegrityViolationException("idempotency failure")).when(idempotencyRecordRepository).save(any(IdempotencyRecord.class));

        assertThatThrownBy(() -> accountService.create(ownerId, request())).isInstanceOf(DataIntegrityViolationException.class);

        assertNoLedgerRows(ownerId);
    }

    @Test
    void reconciliationPersistenceFailureRollsBackAdjustmentProjectionAndEvidence() {
        var ownerId = insertUser("reconciliation-failure");
        var openingAt = Instant.now().minusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var account = accountService.create(ownerId,
                new CreateFinancialAccountRequest(UUID.randomUUID(), "Reconciliation rollback account", AccountKind.CASH_CURRENT, TrackingMode.FULL_LEDGER,
                        "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("25", openingAt)));
        var preview = reconciliationCommandService.preview(ownerId, account.id(),
                new ReconciliationPreviewRequest("rollback", openingAt, openingAt.plusSeconds(10), "25", "30"));
        doThrow(new DataIntegrityViolationException("reconciliation failure")).when(reconciliationRepository).save(any(Reconciliation.class));

        assertThatThrownBy(
                () -> reconciliationCommandService.commit(ownerId, account.id(),
                        new ReconciliationCommitRequest("rollback", openingAt, openingAt.plusSeconds(10), "25", "30", UUID.randomUUID(),
                                preview.projectionVersion(), ReconciliationAction.CREATE_ADJUSTMENT, "Rollback adjustment")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE owner_user_account_id = ?", Integer.class, ownerId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'RECONCILIATION_ADJUSTMENT'", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT ledger_balance FROM ledger.account_balance_projection WHERE owner_user_account_id = ?",
                java.math.BigDecimal.class, ownerId)).isEqualByComparingTo("25");
    }

    @Test
    void activityPersistenceFailureRollsBackTheReconciliationWorkflow() {
        var fixture = adjustmentFixture("activity-failure");
        doThrow(new DataIntegrityViolationException("activity failure")).when(activityRepository).save(any(Activity.class));

        assertThatThrownBy(() -> reconciliationCommandService.commit(fixture.ownerId(), fixture.accountId(), fixture.request(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoReconciliationEffect(fixture.ownerId());
    }

    @Test
    void postingPersistenceFailureRollsBackTheReconciliationWorkflow() {
        var fixture = adjustmentFixture("posting-failure");
        doThrow(new DataIntegrityViolationException("posting failure")).when(moneyPostingRepository).save(any(MoneyPosting.class));

        assertThatThrownBy(() -> reconciliationCommandService.commit(fixture.ownerId(), fixture.accountId(), fixture.request(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoReconciliationEffect(fixture.ownerId());
    }

    @Test
    void projectionPersistenceFailureRollsBackTheReconciliationWorkflow() {
        var fixture = adjustmentFixture("projection-reconciliation-failure");
        doThrow(new DataIntegrityViolationException("projection failure")).when(projectionRepository).save(any(AccountBalanceProjection.class));

        assertThatThrownBy(() -> reconciliationCommandService.commit(fixture.ownerId(), fixture.accountId(), fixture.request(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoReconciliationEffect(fixture.ownerId());
    }

    @Test
    void idempotencyPersistenceFailureRollsBackTheReconciliationWorkflow() {
        var fixture = adjustmentFixture("idempotency-reconciliation-failure");
        doThrow(new DataIntegrityViolationException("idempotency failure")).when(idempotencyRecordRepository).save(any(IdempotencyRecord.class));

        assertThatThrownBy(() -> reconciliationCommandService.commit(fixture.ownerId(), fixture.accountId(), fixture.request(UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertNoReconciliationEffect(fixture.ownerId());
    }

    @Test
    void correctionPersistenceFailureRollsBackReversalReplacementAndProjection() {
        var fixture = adjustmentFixture("correction-failure");
        var original = reconciliationCommandService.commit(fixture.ownerId(), fixture.accountId(), fixture.request(UUID.randomUUID()));
        var replacementPreview = reconciliationCommandService.preview(fixture.ownerId(), fixture.accountId(),
                new ReconciliationPreviewRequest("replacement", fixture.openingAt(), fixture.closingAt(), "25", "29"));
        doThrow(new DataIntegrityViolationException("replacement reconciliation failure")).when(reconciliationRepository).save(any(Reconciliation.class));

        assertThatThrownBy(() -> reconciliationCommandService.correct(fixture.ownerId(), original.id(),
                new ReconciliationCorrectionRequest("replacement", fixture.openingAt(), fixture.closingAt(), "25", "29", UUID.randomUUID(),
                        replacementPreview.projectionVersion(), ReconciliationAction.CREATE_ADJUSTMENT, "replacement adjustment", "correction rollback")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("reconciliation", fixture.ownerId())).isEqualTo(1);
        assertThat(countActivity(fixture.ownerId(), "REVERSAL")).isZero();
        assertThat(countActivity(fixture.ownerId(), "RECONCILIATION_ADJUSTMENT")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT ledger_balance FROM ledger.account_balance_projection WHERE owner_user_account_id = ?",
                java.math.BigDecimal.class, fixture.ownerId())).isEqualByComparingTo("30");
    }

    private AdjustmentFixture adjustmentFixture(String suffix) {
        var ownerId = insertUser(suffix);
        var openingAt = Instant.now().minusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var closingAt = openingAt.plusSeconds(10);
        var account = accountService.create(ownerId, new CreateFinancialAccountRequest(UUID.randomUUID(), "Reconciliation " + suffix, AccountKind.CASH_CURRENT,
                TrackingMode.FULL_LEDGER, "USD", "UTC", NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("25", openingAt)));
        var preview = reconciliationCommandService.preview(ownerId, account.id(),
                new ReconciliationPreviewRequest("fixture", openingAt, closingAt, "25", "30"));
        return new AdjustmentFixture(ownerId, account.id(), openingAt, closingAt, preview.projectionVersion());
    }

    private void assertNoReconciliationEffect(UUID ownerId) {
        assertThat(count("reconciliation", ownerId)).isZero();
        assertThat(countActivity(ownerId, "RECONCILIATION_ADJUSTMENT")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.money_posting WHERE owner_user_account_id = ? AND posting_role = 'ADJUSTMENT'",
                Integer.class, ownerId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT ledger_balance FROM ledger.account_balance_projection WHERE owner_user_account_id = ?",
                java.math.BigDecimal.class, ownerId)).isEqualByComparingTo("25");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.idempotency_record WHERE owner_user_account_id = ? AND operation_scope = 'ledger.reconciliation.commit'",
                Integer.class, ownerId)).isZero();
    }

    private int count(String table, UUID ownerId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger." + table + " WHERE owner_user_account_id = ?", Integer.class, ownerId);
    }

    private int countActivity(UUID ownerId, String activityType) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = ?", Integer.class, ownerId,
                activityType);
    }

    private record AdjustmentFixture(UUID ownerId, UUID accountId, Instant openingAt, Instant closingAt, long projectionVersion) {
        ReconciliationCommitRequest request(UUID clientRequestId) {
            return new ReconciliationCommitRequest("fixture", openingAt, closingAt, "25", "30", clientRequestId, projectionVersion,
                    ReconciliationAction.CREATE_ADJUSTMENT, "fixture adjustment");
        }
    }

    private CreateFinancialAccountRequest request() {
        return new CreateFinancialAccountRequest(UUID.randomUUID(), "Rollback account", AccountKind.CASH_CURRENT, TrackingMode.FULL_LEDGER, "USD", "UTC",
                NegativeBalancePolicy.HARD_FLOOR, null, new OpeningStateRequest("25", Instant.now().minusSeconds(10)));
    }

    private UUID insertUser(String suffix) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var email = id + "+" + suffix + "@rollback.test";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)" + " VALUES (?, ?, ?, ?, ?)", id, email, email, now,
                now));
        return id;
    }

    private void assertNoLedgerRows(UUID ownerId) {
        for (var table : new String[]{"financial_account", "account_cash_pocket", "activity", "money_posting", "account_balance_projection", "reconciliation",
                "idempotency_record"}) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger." + table + " WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .as(table).isZero();
        }
    }
}
