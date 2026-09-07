package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.PostingRole;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.AccountCashPocketRepository;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.FinancialAccountRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.infrastructure.ReconciliationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
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
class FinancialAccountMappingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-16T09:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Autowired
    FinancialAccountRepository accountRepository;

    @Autowired
    AccountCashPocketRepository pocketRepository;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    MoneyPostingRepository postingRepository;

    @Autowired
    AccountBalanceProjectionRepository projectionRepository;

    @Autowired
    ReconciliationRepository reconciliationRepository;

    @Test
    void financialAccountCashPocketActivityPostingAndProjectionMappingsRoundTrip() {
        var ownerId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var pocketId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var postingId = UUID.randomUUID();
        var projectionId = UUID.randomUUID();
        insertUser(ownerId);
        var timestamp = CREATED_AT.atOffset(ZoneOffset.UTC);

        jdbcTemplate.update(
                "INSERT INTO ledger.financial_account" + " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode," +
                        " negative_balance_policy, currency_code, time_zone, created_at, updated_at, version)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                accountId, ownerId, "Mapping Cash", "MAPPING CASH", "CASH_CURRENT", "FULL_LEDGER", "HARD_FLOOR", "USD", "UTC", timestamp, timestamp);
        jdbcTemplate.update(
                "INSERT INTO ledger.account_cash_pocket" + " (id, owner_user_account_id, financial_account_id, currency_code, coverage_status," +
                        " coverage_from, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                pocketId, ownerId, accountId, "USD", "KNOWN_FROM_OPENING", timestamp, timestamp, timestamp);
        jdbcTemplate.update("INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision)" + " VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                activityId, ownerId, UUID.randomUUID(), "mapping.test", "OPENING_BALANCE", "HISTORICAL_FACT", timestamp, timestamp, "USER_ENTERED", "ALLOWED");
        jdbcTemplate.update(
                "INSERT INTO ledger.money_posting" + " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id," +
                        " currency_code, amount, posting_role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?)",
                postingId, ownerId, activityId, accountId, pocketId, "USD", "123.4500", "OPENING", timestamp);
        jdbcTemplate.update(
                "INSERT INTO ledger.account_balance_projection" + " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code," +
                        " ledger_balance, last_applied_recorded_at, last_applied_activity_id, updated_at, version)" +
                        " VALUES (?, ?, ?, ?, ?, ?::numeric, ?, ?, ?, 0)",
                projectionId, ownerId, accountId, pocketId, "USD", "123.4500", timestamp, activityId, timestamp);
        jdbcTemplate.update("UPDATE ledger.financial_account SET current_opening_activity_id = ? WHERE id = ?", activityId, accountId);
        entityManager.clear();

        var account = accountRepository.findById(accountId).orElseThrow();
        var pocket = pocketRepository.findById(pocketId).orElseThrow();
        var activity = activityRepository.findById(activityId).orElseThrow();
        var posting = postingRepository.findById(postingId).orElseThrow();
        var projection = projectionRepository.findById(projectionId).orElseThrow();

        assertThat(account.getAccountKind()).isEqualTo(dev.canverse.stocks.ledger.domain.AccountKind.CASH_CURRENT);
        assertThat(account.getTrackingMode()).isEqualTo(TrackingMode.FULL_LEDGER);
        assertThat(account.getNegativeBalancePolicy()).isEqualTo(NegativeBalancePolicy.HARD_FLOOR);
        assertThat(account.getCurrentOpeningActivityId()).isEqualTo(activityId);
        assertThat(pocket.getCoverageStatus()).isEqualTo(CoverageStatus.KNOWN_FROM_OPENING);
        assertThat(pocket.getFinancialAccount().getId()).isEqualTo(accountId);
        assertThat(activity.getActivityType()).isEqualTo(ActivityType.OPENING_BALANCE);
        assertThat(activity.getRecordingMode()).isEqualTo(RecordingMode.HISTORICAL_FACT);
        assertThat(activity.getPolicyDecision()).isEqualTo(PolicyDecision.ALLOWED);
        assertThat(posting.getPostingRole()).isEqualTo(PostingRole.OPENING);
        assertThat(FinancialAmount.of(posting.getAmount()).canonical()).isEqualTo("123.45");
        assertThat(projection.getCashPocket().getId()).isEqualTo(pocketId);
        assertThat(projection.balance().canonical()).isEqualTo("123.45");
        assertThat(projection.getVersion()).isZero();

        var reconciliationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ledger.reconciliation" + " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code," +
                        " statement_reference, statement_opening_at, statement_closing_at, statement_opening_balance," +
                        " statement_closing_balance, ledger_opening_balance, ledger_closing_balance_before_adjustment," +
                        " period_net_posted_amount, closing_difference, period_posting_count," +
                        " total_posting_count_through_closing, resolution, source_kind, created_at)" +
                        " VALUES (?, ?, ?, ?, 'USD', ?, ?, ?, 123.45, 123.45, 123.45, 123.45, 0, 0, 0, 1, 'BALANCED', 'USER_ENTERED', ?)",
                reconciliationId, ownerId, accountId, pocketId, "Mapping statement", timestamp, timestamp.plusSeconds(60), timestamp);
        entityManager.clear();
        var reconciliation = reconciliationRepository.findOwned(ownerId, reconciliationId).orElseThrow();
        assertThat(reconciliation.getResolution()).isEqualTo(ReconciliationResolution.BALANCED);
        assertThat(reconciliation.getCurrencyCode()).isEqualTo("USD");
        assertThat(reconciliation.getStatementOpeningBalance()).isEqualByComparingTo("123.45");
        assertThat(reconciliation.getTotalPostingCountThroughClosing()).isEqualTo(1);
    }

    private void insertUser(UUID ownerId) {
        var timestamp = CREATED_AT.atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)" + " VALUES (?, ?, ?, ?, ?)", ownerId,
                ownerId + "@mapping.test", ownerId + "@mapping.test", timestamp, timestamp);
    }
}
