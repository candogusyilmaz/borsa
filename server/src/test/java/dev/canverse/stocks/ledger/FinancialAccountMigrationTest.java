package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FinancialAccountMigrationTest {

    private static final Set<String> LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "reconciliation");

    private static final Set<String> LEDGER_CONSTRAINTS = Set.of("ck_ledger_account_balance_projection_version_non_negative",
            "ck_ledger_account_cash_pocket_coverage_status", "ck_ledger_account_cash_pocket_version_non_negative", "ck_ledger_activity_command_sequence",
            "ck_ledger_activity_correction_reason", "ck_ledger_activity_economic_sequence", "ck_ledger_activity_no_self_link",
            "ck_ledger_activity_operation_scope", "ck_ledger_activity_policy_decision", "ck_ledger_activity_policy_shape", "ck_ledger_activity_recording_mode",
            "ck_ledger_activity_reversal_shape", "ck_ledger_activity_source_kind", "ck_ledger_activity_supersession_shape", "ck_ledger_activity_type",
            "ck_ledger_financial_account_authorized_limit", "ck_ledger_financial_account_kind", "ck_ledger_financial_account_name",
            "ck_ledger_financial_account_name_normalized", "ck_ledger_financial_account_policy_shape", "ck_ledger_financial_account_time_zone",
            "ck_ledger_financial_account_tracking", "ck_ledger_financial_account_version_non_negative", "ck_ledger_idempotency_operation_scope",
            "ck_ledger_idempotency_request_hash", "ck_ledger_idempotency_result_kind", "ck_ledger_idempotency_snapshot_object",
            "ck_ledger_idempotency_snapshot_size", "ck_ledger_money_posting_amount_zero_shape", "ck_ledger_money_posting_role",
            "ck_ledger_money_posting_role_sign", "ck_ledger_reconciliation_counts", "ck_ledger_reconciliation_equation",
            "ck_ledger_reconciliation_no_self_supersession", "ck_ledger_reconciliation_resolution", "ck_ledger_reconciliation_resolution_shape",
            "ck_ledger_reconciliation_source_kind", "ck_ledger_reconciliation_statement_reference", "ck_ledger_reconciliation_time_order",
            "fk_ledger_account_balance_projection_account", "fk_ledger_account_balance_projection_account_currency",
            "fk_ledger_account_balance_projection_owner", "fk_ledger_account_balance_projection_pocket", "fk_ledger_account_balance_projection_pocket_identity",
            "fk_ledger_account_balance_projection_watermark_activity", "fk_ledger_account_cash_pocket_account_currency",
            "fk_ledger_account_cash_pocket_account_owner", "fk_ledger_account_cash_pocket_currency", "fk_ledger_account_cash_pocket_owner",
            "fk_ledger_activity_owner", "fk_ledger_activity_reverses", "fk_ledger_activity_supersedes", "fk_ledger_financial_account_currency",
            "fk_ledger_financial_account_current_opening", "fk_ledger_financial_account_owner", "fk_ledger_idempotency_record_owner",
            "fk_ledger_money_posting_account", "fk_ledger_money_posting_account_currency", "fk_ledger_money_posting_activity", "fk_ledger_money_posting_owner",
            "fk_ledger_money_posting_pocket", "fk_ledger_money_posting_pocket_identity", "fk_ledger_reconciliation_account_currency",
            "fk_ledger_reconciliation_account_owner", "fk_ledger_reconciliation_adjustment_activity", "fk_ledger_reconciliation_owner",
            "fk_ledger_reconciliation_pocket_identity", "fk_ledger_reconciliation_supersedes", "pk_ledger_account_balance_projection",
            "pk_ledger_account_cash_pocket", "pk_ledger_activity", "pk_ledger_financial_account", "pk_ledger_idempotency_record", "pk_ledger_money_posting",
            "pk_ledger_reconciliation", "uq_ledger_account_balance_projection_pocket", "uq_ledger_account_cash_pocket_account_currency",
            "uq_ledger_account_cash_pocket_identity", "uq_ledger_account_cash_pocket_owner_id", "uq_ledger_activity_operation", "uq_ledger_activity_owner_id",
            "uq_ledger_activity_owner_id_type", "uq_ledger_activity_reversal", "uq_ledger_financial_account_id_currency",
            "uq_ledger_financial_account_owner_id", "uq_ledger_idempotency_owner_scope_request", "uq_ledger_reconciliation_adjustment_activity",
            "uq_ledger_reconciliation_owner_account_id", "uq_ledger_reconciliation_owner_id", "uq_ledger_reconciliation_supersedes");

    private static final Set<String> LEDGER_INDEXES = Set.of("ix_ledger_account_balance_projection_owner_account",
            "ix_ledger_account_cash_pocket_owner_account", "ix_ledger_activity_owner_effective", "ix_ledger_activity_owner_recorded",
            "ix_ledger_financial_account_owner_name", "ix_ledger_money_posting_account", "ix_ledger_money_posting_activity",
            "uix_ledger_financial_account_active_name", "ix_ledger_reconciliation_owner_account_closing");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void v4AddsExactlyTheReconciliationTableAndExpectedLedgerShape() {
        assertThat(flyway.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4");

        var tables = Set.copyOf(jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class));
        assertThat(tables).isEqualTo(LEDGER_TABLES);

        assertNumericColumn("financial_account", "authorized_limit");
        assertNumericColumn("money_posting", "amount");
        assertNumericColumn("account_balance_projection", "ledger_balance");
        for (var column : new String[]{"statement_opening_balance", "statement_closing_balance", "ledger_opening_balance",
                "ledger_closing_balance_before_adjustment", "period_net_posted_amount", "closing_difference", "adjustment_amount"}) {
            assertNumericColumn("reconciliation", column);
        }

        var constraints = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace" + " WHERE n.nspname = 'ledger'", String.class));
        assertThat(constraints).isEqualTo(LEDGER_CONSTRAINTS);

        var indexes = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes" + " WHERE schemaname = 'ledger' AND (indexname LIKE 'ix_%' OR indexname LIKE 'uix_%')", String.class));
        assertThat(indexes).isEqualTo(LEDGER_INDEXES);
    }

    @Test
    void noExcludedLaterLedgerStructuresWereAddedToV4() {
        var forbidden = jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'" +
                " AND table_name IN ('security_posting', 'activity_split', 'import_batch'," +
                " 'spending_entry', 'investment_position', 'household_member', 'observation', 'job')", String.class);
        assertThat(forbidden).isEmpty();
    }

    @Test
    void v3DatabaseUpgradesToV4WithoutSkippingMigrationsOrLosingRepresentativeRows() throws Exception {
        var databaseName = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var adminUrl = postgres.getJdbcUrl();
        var targetUrl = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + databaseName;
        try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE " + databaseName);
        }
        try {
            var v3 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("3")).load();
            v3.migrate();
            assertThat(v3.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3");

            var v3Jdbc = new JdbcTemplate(new DriverManagerDataSource(targetUrl, postgres.getUsername(), postgres.getPassword()));
            var ownerId = UUID.randomUUID();
            var accountId = UUID.randomUUID();
            var pocketId = UUID.randomUUID();
            var activityId = UUID.randomUUID();
            var now = OffsetDateTime.now(ZoneOffset.UTC);
            var openingAt = now.minusMinutes(1);
            v3Jdbc.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", ownerId,
                    ownerId + "@v3-upgrade.test", ownerId + "@v3-upgrade.test", now, now);
            v3Jdbc.update(
                    "INSERT INTO ledger.financial_account" + " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode," +
                            " negative_balance_policy, currency_code, time_zone, created_at, updated_at, version)" +
                            " VALUES (?, ?, 'V3 Account', 'V3 ACCOUNT', 'CASH_CURRENT', 'FULL_LEDGER'," + " 'HARD_FLOOR', 'USD', 'UTC', ?, ?, 0)",
                    accountId, ownerId, now, now);
            v3Jdbc.update(
                    "INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                            " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision)" +
                            " VALUES (?, ?, ?, 'v3.upgrade', 0, 'OPENING_BALANCE', 'HISTORICAL_FACT', ?, ?, 'USER_ENTERED', 'ALLOWED')",
                    activityId, ownerId, UUID.randomUUID(), openingAt, now);
            v3Jdbc.update("UPDATE ledger.financial_account SET current_opening_activity_id = ? WHERE id = ?", activityId, accountId);
            v3Jdbc.update(
                    "INSERT INTO ledger.account_cash_pocket" + " (id, owner_user_account_id, financial_account_id, currency_code, coverage_status," +
                            " coverage_from, created_at, updated_at, version) VALUES (?, ?, ?, 'USD', 'KNOWN_FROM_OPENING', ?, ?, ?, 0)",
                    pocketId, ownerId, accountId, openingAt, now, now);
            v3Jdbc.update(
                    "INSERT INTO ledger.money_posting" + " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id," +
                            " currency_code, amount, posting_role, created_at) VALUES (?, ?, ?, ?, ?, 'USD', 10, 'OPENING', ?)",
                    UUID.randomUUID(), ownerId, activityId, accountId, pocketId, now);
            v3Jdbc.update("INSERT INTO ledger.account_balance_projection" +
                    " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code," +
                    " ledger_balance, last_applied_recorded_at, last_applied_activity_id, updated_at, version)" + " VALUES (?, ?, ?, ?, 'USD', 10, ?, ?, ?, 0)",
                    UUID.randomUUID(), ownerId, accountId, pocketId, now, activityId, now);

            var latest = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").load();
            latest.migrate();
            assertThat(latest.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4");
            assertThat(v3Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.financial_account WHERE id = ?", Integer.class, accountId)).isEqualTo(1);
            assertThat(v3Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE id = ?", Integer.class, activityId)).isEqualTo(1);
            assertThat(v3Jdbc.queryForObject("SELECT ledger_balance FROM ledger.account_balance_projection WHERE financial_account_id = ?", String.class,
                    accountId)).isEqualTo("10.000000000000000000");
            try (var connection = DriverManager.getConnection(targetUrl, postgres.getUsername(), postgres.getPassword());
                    var statement = connection.createStatement(); var result = statement.executeQuery("SELECT to_regclass('ledger.money_posting')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("ledger.money_posting");
            }
        } finally {
            try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
                admin.createStatement().execute("DROP DATABASE " + databaseName);
            }
        }
    }

    @Test
    void databaseRejectsInvalidEnumsPolicyShapesSignsAndOwnerForeignKeys() {
        var ownerId = insertUser();
        var accountId = insertAccount(ownerId);
        var pocketId = insertPocket(ownerId, accountId);
        var currentActionId = insertActivity(ownerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");

        assertThatThrownBy(() -> insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "CURRENT_ACTION", "ALLOWED", "invalid adjustment"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                        " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision)" +
                        " VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, 'USER_ENTERED', ?)",
                UUID.randomUUID(), ownerId, UUID.randomUUID(), "invalid-policy", "OPENING_BALANCE", "HISTORICAL_FACT", timestamp(), timestamp(),
                "NOT_APPLICABLE")).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                        " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision)" +
                        " VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, 'USER_ENTERED', 'ALLOWED')",
                UUID.randomUUID(), ownerId, UUID.randomUUID(), "invalid-enum", "UNKNOWN", "CURRENT_ACTION", timestamp(), timestamp()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ledger.money_posting" + " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id," +
                        " currency_code, amount, posting_role, created_at)" + " VALUES (?, ?, ?, ?, ?, 'USD', ?::numeric, 'DEPOSIT', ?)",
                UUID.randomUUID(), ownerId, currentActionId, accountId, pocketId, "-1", timestamp())).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ledger.financial_account" + " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode," +
                        " negative_balance_policy, currency_code, time_zone, created_at, updated_at, version)" +
                        " VALUES (?, ?, 'Foreign owner', 'FOREIGN OWNER', 'CASH_CURRENT', 'FULL_LEDGER'," + " 'HARD_FLOOR', 'USD', 'UTC', ?, ?, 0)",
                UUID.randomUUID(), UUID.randomUUID(), timestamp(), timestamp())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databasePreservesV3PostingRoleSignAndZeroShapesWhileAllowingSignedAdjustments() {
        var ownerId = insertUser();
        var accountId = insertAccount(ownerId);
        var pocketId = insertPocket(ownerId, accountId);
        var activityId = insertActivity(ownerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");

        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "0", "OPENING");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "-1", "OPENING");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "0", "REVERSAL");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "1", "REVERSAL");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "1", "DEPOSIT");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "-1", "WITHDRAWAL");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "-1", "TRANSFER_SOURCE");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "1", "TRANSFER_DESTINATION");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "1", "ADJUSTMENT");
        insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", "-1", "ADJUSTMENT");

        for (var invalid : new String[][]{{"-1", "DEPOSIT"}, {"0", "DEPOSIT"}, {"1", "WITHDRAWAL"}, {"0", "WITHDRAWAL"}, {"1", "TRANSFER_SOURCE"},
                {"0", "TRANSFER_SOURCE"}, {"-1", "TRANSFER_DESTINATION"}, {"0", "TRANSFER_DESTINATION"}, {"0", "ADJUSTMENT"}}) {
            assertThatThrownBy(() -> insertRawPosting(ownerId, activityId, accountId, pocketId, "USD", invalid[0], invalid[1]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void databaseRejectsEveryInvalidAccountPolicyKindAndLimitCombination() {
        var ownerId = insertUser();

        assertThatThrownBy(() -> insertRawAccount(ownerId, "CASH_CURRENT", "HOLDINGS_ONLY", null, "USD", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "BROKERAGE", "HOLDINGS_ONLY", "HARD_FLOOR", "USD", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "CREDIT_CARD", "FULL_LEDGER", "HARD_FLOOR", "USD", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "CASH_SAVINGS", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "50"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "HARD_FLOOR", "USD", "50"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);

        var validId = insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "50");
        assertThat(jdbcTemplate.queryForObject("SELECT authorized_limit FROM ledger.financial_account WHERE id = ?", String.class, validId))
                .isEqualTo("50.000000000000000000");
    }

    @Test
    void databaseRejectsCrossOwnerAccountPocketCurrencyAndActivityReferences() {
        var firstOwnerId = insertUser();
        var secondOwnerId = insertUser();
        var firstAccountId = insertAccount(firstOwnerId);
        var secondAccountId = insertAccount(secondOwnerId);
        var firstPocketId = insertPocket(firstOwnerId, firstAccountId);
        var secondPocketId = insertPocket(secondOwnerId, secondAccountId);
        var firstActivityId = insertActivity(firstOwnerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");
        var secondActivityId = insertActivity(secondOwnerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");

        assertThatThrownBy(() -> insertRawPocket(secondOwnerId, firstAccountId, "USD")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPocket(firstOwnerId, firstAccountId, "EUR")).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertRawPosting(firstOwnerId, firstActivityId, secondAccountId, secondPocketId, "USD", "1", "DEPOSIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPosting(firstOwnerId, firstActivityId, firstAccountId, secondPocketId, "USD", "1", "DEPOSIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPosting(firstOwnerId, firstActivityId, firstAccountId, firstPocketId, "EUR", "1", "DEPOSIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawPosting(firstOwnerId, secondActivityId, firstAccountId, firstPocketId, "USD", "1", "DEPOSIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesReversalIdempotencyAndNumericBoundaries() {
        var ownerId = insertUser();
        var originalId = insertActivity(ownerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");
        insertRawReversal(ownerId, originalId);
        assertThatThrownBy(() -> insertRawReversal(ownerId, originalId)).isInstanceOf(DataIntegrityViolationException.class);

        var requestId = UUID.randomUUID();
        insertRawIdempotency(ownerId, "migration.unique", requestId, "{}");
        assertThatThrownBy(() -> insertRawIdempotency(ownerId, "migration.unique", requestId, "{}")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawIdempotency(ownerId, "migration.scalar-array", UUID.randomUUID(), "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawIdempotency(ownerId, "migration.scalar-null", UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class);

        var maximum = insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "99999999999999999999.999999999999999999");
        assertThat(maximum).isNotNull();
        assertThatThrownBy(
                () -> insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "100000000000000000000.000000000000000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
        var databaseScale = insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "AUTHORIZED_LIMIT", "USD", "1.0000000000000000001");
        assertThat(jdbcTemplate.queryForObject("SELECT authorized_limit FROM ledger.financial_account WHERE id = ?", String.class, databaseScale))
                .isEqualTo("1.000000000000000000");

        var accountId = insertAccount(ownerId);
        var pocketId = insertPocket(ownerId, accountId);
        var maximumReconciliationValue = "99999999999999999999.999999999999999999";
        assertThat(insertRawReconciliation(ownerId, accountId, pocketId, "USD", maximumReconciliationValue, maximumReconciliationValue,
                maximumReconciliationValue, maximumReconciliationValue, "0", "0", null, 0, 0, "BALANCED", null, null, null)).isNotNull();
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100000000000000000000", maximumReconciliationValue,
                maximumReconciliationValue, maximumReconciliationValue, "0", "0", null, 0, 0, "BALANCED", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidReconciliationIdentityEquationsBoundsAndLinks() {
        var ownerId = insertUser();
        var otherOwnerId = insertUser();
        var accountId = insertAccount(ownerId);
        var sameOwnerOtherAccountId = insertAccount(ownerId);
        var otherAccountId = insertAccount(otherOwnerId);
        var pocketId = insertPocket(ownerId, accountId);
        var sameOwnerOtherPocketId = insertPocket(ownerId, sameOwnerOtherAccountId);
        var otherPocketId = insertPocket(otherOwnerId, otherAccountId);
        var activityId = insertActivity(ownerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");
        var otherActivityId = insertActivity(otherOwnerId, "CASH_DEPOSIT", "CURRENT_ACTION", "ALLOWED");
        var adjustmentActivityId = insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "adjustment");
        var otherAdjustmentActivityId = insertActivity(otherOwnerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "other adjustment");
        var extraAdjustmentActivityId = insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "extra adjustment");

        var validId = insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED", null, null,
                null);
        assertThat(validId).isNotNull();
        var adjustedId = insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "105", "100", "100", "0", "5", "5", 0, 1, "ADJUSTED",
                adjustmentActivityId, null, "adjustment");
        assertThat(adjustedId).isNotNull();
        assertThatThrownBy(() -> insertRawReconciliationWithReference(ownerId, accountId, pocketId, " ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliationWithReference(ownerId, accountId, pocketId, "r".repeat(201)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "105", "100", "100", "0", "5", "5", 0, 1, "ADJUSTED",
                extraAdjustmentActivityId, null, " padded ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "105", "100", "100", "0", "5", "5", 0, 1, "ADJUSTED",
                extraAdjustmentActivityId, null, "r".repeat(501))).isInstanceOf(DataIntegrityViolationException.class);
        var equalTime = timestamp();
        assertThatThrownBy(() -> insertRawBalancedReconciliationAt(ownerId, accountId, pocketId, equalTime, equalTime))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawBalancedReconciliationAt(ownerId, accountId, pocketId, equalTime, equalTime.minusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> updateCommitted("UPDATE ledger.reconciliation SET statement_reference = ? WHERE id = ?", "rewritten", validId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("DELETE FROM ledger.reconciliation WHERE id = ?", validId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("UPDATE ledger.reconciliation SET statement_reference = ? WHERE id = ?", " padded ", validId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("UPDATE ledger.reconciliation SET statement_reference = ? WHERE id = ?", "r".repeat(201), validId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("UPDATE ledger.reconciliation SET adjustment_reason = ? WHERE id = ?", " padded ", adjustedId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawReconciliation(otherOwnerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        var selfId = UUID.randomUUID();
        assertThatThrownBy(() -> insertRawReconciliation(selfId, ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1,
                "BALANCED", null, selfId, null)).isInstanceOf(DataIntegrityViolationException.class);
        insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED", null, validId, null);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, validId, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, otherPocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "99", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED", null,
                null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "105", "100", "100", "0", "5", "5", 0, 1, "ADJUSTED",
                activityId, null, "wrong activity type")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "105", "100", "100", "0", "5", "5", 0, 1, "ADJUSTED",
                otherAdjustmentActivityId, null, "wrong owner")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "106", "100", "100", "0", "6", "6", 0, 1, "ADJUSTED",
                adjustmentActivityId, null, "duplicate adjustment link")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "EUR", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "99", "100", "0", "0", null, 0, 1, "BALANCED", null,
                null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, -1, 1, "BALANCED",
                null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "ADJUSTED",
                null, null, "missing adjustment")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                otherActivityId, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                activityId, validId, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, UUID.randomUUID(), null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawReconciliation(ownerId, sameOwnerOtherAccountId, sameOwnerOtherPocketId, "USD", "100", "100", "100", "100", "0", "0",
                null, 0, 1, "BALANCED", null, validId, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonOwnerCascadeDeletionCannotRemoveReconciliationEvidence() {
        var ownerId = insertUser();
        var accountId = insertAccount(ownerId);
        var pocketId = insertPocket(ownerId, accountId);
        var adjustmentActivityId = insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "cascade guard");
        var reconciliationId = insertRawReconciliation(ownerId, accountId, pocketId, "USD", "100", "101", "100", "100", "0", "1", "1", 0, 1, "ADJUSTED",
                adjustmentActivityId, null, "cascade guard");

        assertThatThrownBy(() -> updateCommitted("DELETE FROM ledger.financial_account WHERE id = ?", accountId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("DELETE FROM ledger.account_cash_pocket WHERE id = ?", pocketId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> updateCommitted("DELETE FROM ledger.activity WHERE id = ?", adjustmentActivityId)).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE id = ?", Integer.class, reconciliationId)).isEqualTo(1);
    }

    @Test
    void ownerDeletionCascadesAllLedgerRowsIncludingPostings() {
        var ownerId = insertUser();
        var accountId = insertAccount(ownerId);
        var pocketId = insertPocket(ownerId, accountId);
        var activityId = insertActivity(ownerId, "OPENING_BALANCE", "HISTORICAL_FACT", "ALLOWED");
        updateCommitted(
                "INSERT INTO ledger.money_posting" + " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id," +
                        " currency_code, amount, posting_role, created_at)" + " VALUES (?, ?, ?, ?, ?, 'USD', 10, 'OPENING', ?)",
                UUID.randomUUID(), ownerId, activityId, accountId, pocketId, timestamp());
        updateCommitted("INSERT INTO ledger.account_balance_projection" + " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code," +
                " ledger_balance, last_applied_recorded_at, last_applied_activity_id, updated_at, version)" + " VALUES (?, ?, ?, ?, 'USD', 10, ?, ?, ?, 0)",
                UUID.randomUUID(), ownerId, accountId, pocketId, timestamp(), activityId, timestamp());
        updateCommitted("UPDATE ledger.financial_account SET current_opening_activity_id = ? WHERE id = ?", activityId, accountId);
        insertRawReconciliation(ownerId, accountId, pocketId, "USD", "10", "10", "10", "10", "0", "0", null, 0, 1, "BALANCED", null, null, null);
        var adjustmentActivityId = insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "owner deletion adjustment");
        var replacementAdjustmentActivityId = insertActivity(ownerId, "RECONCILIATION_ADJUSTMENT", "HISTORICAL_FACT", "ALLOWED", "owner deletion replacement");
        var adjustedId = insertRawReconciliation(ownerId, accountId, pocketId, "USD", "10", "12", "10", "10", "0", "2", "2", 0, 2, "ADJUSTED",
                adjustmentActivityId, null, "owner deletion adjustment");
        insertRawReconciliation(ownerId, accountId, pocketId, "USD", "10", "11", "10", "10", "0", "1", "1", 0, 2, "ADJUSTED", replacementAdjustmentActivityId,
                adjustedId, "owner deletion replacement");

        updateCommitted("DELETE FROM identity.user_account WHERE id = ?", ownerId);

        for (var table : LEDGER_TABLES) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger." + table + " WHERE owner_user_account_id = ?", Integer.class, ownerId))
                    .isZero();
        }
    }

    @Test
    void boundedJsonbAndTransactionRollbackAreEnforced() {
        var ownerId = insertUser();
        var now = timestamp();
        var oversized = "{\"value\":\"" + "x".repeat(33_000) + "\"}";
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ledger.idempotency_record" + " (id, owner_user_account_id, operation_scope, client_request_id, request_hash," +
                        " result_resource_kind, result_resource_id, result_snapshot, created_at)" +
                        " VALUES (?, ?, 'migration.test', ?, ?, 'ACCOUNT', ?, ?::jsonb, ?)",
                UUID.randomUUID(), ownerId, UUID.randomUUID(), "a".repeat(64), UUID.randomUUID(), oversized, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        var transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "INSERT INTO ledger.financial_account" + " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode," +
                            " negative_balance_policy, currency_code, time_zone, created_at, updated_at, version)" +
                            " VALUES (?, ?, 'Rolled back', 'ROLLED BACK', 'CASH_CURRENT', 'FULL_LEDGER'," + " 'HARD_FLOOR', 'USD', 'UTC', ?, ?, 0)",
                    UUID.randomUUID(), ownerId, now, now);
            throw new IllegalStateException("rollback proof");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
    }

    private UUID insertUser() {
        var id = UUID.randomUUID();
        var email = id + "@migration.test";
        updateCommitted("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, email, email,
                timestamp(), timestamp());
        return id;
    }

    private UUID insertAccount(UUID ownerId) {
        return insertRawAccount(ownerId, "CASH_CURRENT", "FULL_LEDGER", "HARD_FLOOR", "USD", null);
    }

    private UUID insertRawAccount(UUID ownerId, String accountKind, String trackingMode, String policy, String currency, String authorizedLimit) {
        var id = UUID.randomUUID();
        var name = "Account " + id;
        var now = timestamp();
        updateCommitted(
                "INSERT INTO ledger.financial_account" + " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode," +
                        " negative_balance_policy, currency_code, time_zone, authorized_limit, created_at, updated_at, version)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UTC', ?::numeric, ?, ?, 0)",
                id, ownerId, name, name.toUpperCase(), accountKind, trackingMode, policy, currency, authorizedLimit, now, now);
        return id;
    }

    private UUID insertPocket(UUID ownerId, UUID accountId) {
        var id = UUID.randomUUID();
        updateCommitted(
                "INSERT INTO ledger.account_cash_pocket" + " (id, owner_user_account_id, financial_account_id, currency_code, coverage_status," +
                        " coverage_from, created_at, updated_at, version) VALUES (?, ?, ?, 'USD', 'KNOWN_FROM_OPENING', ?, ?, ?, 0)",
                id, ownerId, accountId, timestamp(), timestamp(), timestamp());
        return id;
    }

    private UUID insertActivity(UUID ownerId, String type, String mode, String decision) {
        return insertActivity(ownerId, type, mode, decision, null);
    }

    private UUID insertActivity(UUID ownerId, String type, String mode, String decision, String correctionReason) {
        var id = UUID.randomUUID();
        updateCommitted(
                "INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                        " correction_reason, activity_type, recording_mode, effective_at, recorded_at," + " source_kind, policy_decision)" +
                        " VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 'USER_ENTERED', ?)",
                id, ownerId, UUID.randomUUID(), "migration." + id, correctionReason, type, mode, timestamp(), timestamp(), decision);
        return id;
    }

    private void insertRawPocket(UUID ownerId, UUID accountId, String currency) {
        updateCommitted(
                "INSERT INTO ledger.account_cash_pocket" + " (id, owner_user_account_id, financial_account_id, currency_code, coverage_status," +
                        " coverage_from, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'KNOWN_FROM_OPENING', ?, ?, ?, 0)",
                UUID.randomUUID(), ownerId, accountId, currency, timestamp(), timestamp(), timestamp());
    }

    private void insertRawPosting(UUID ownerId, UUID activityId, UUID accountId, UUID pocketId, String currency, String amount, String role) {
        updateCommitted(
                "INSERT INTO ledger.money_posting" + " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id," +
                        " currency_code, amount, posting_role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?)",
                UUID.randomUUID(), ownerId, activityId, accountId, pocketId, currency, amount, role, timestamp());
    }

    private UUID insertRawReconciliation(UUID ownerId, UUID accountId, UUID pocketId, String currency, String statementOpeningBalance,
            String statementClosingBalance, String ledgerOpeningBalance, String ledgerClosingBalance, String periodNetPostedAmount, String closingDifference,
            String adjustmentAmount, long periodPostingCount, long totalPostingCount, String resolution, UUID adjustmentActivityId,
            UUID supersedesReconciliationId, String adjustmentReason) {
        return insertRawReconciliation(UUID.randomUUID(), ownerId, accountId, pocketId, currency, statementOpeningBalance, statementClosingBalance,
                ledgerOpeningBalance, ledgerClosingBalance, periodNetPostedAmount, closingDifference, adjustmentAmount, periodPostingCount, totalPostingCount,
                resolution, adjustmentActivityId, supersedesReconciliationId, adjustmentReason);
    }

    private UUID insertRawReconciliation(UUID id, UUID ownerId, UUID accountId, UUID pocketId, String currency, String statementOpeningBalance,
            String statementClosingBalance, String ledgerOpeningBalance, String ledgerClosingBalance, String periodNetPostedAmount, String closingDifference,
            String adjustmentAmount, long periodPostingCount, long totalPostingCount, String resolution, UUID adjustmentActivityId,
            UUID supersedesReconciliationId, String adjustmentReason) {
        var closingAt = timestamp();
        return insertRawReconciliationAt(id, ownerId, accountId, pocketId, currency, statementOpeningBalance, statementClosingBalance, ledgerOpeningBalance,
                ledgerClosingBalance, periodNetPostedAmount, closingDifference, adjustmentAmount, periodPostingCount, totalPostingCount, resolution,
                adjustmentActivityId, supersedesReconciliationId, adjustmentReason, closingAt.minusMinutes(1), closingAt);
    }

    private UUID insertRawReconciliationAt(UUID id, UUID ownerId, UUID accountId, UUID pocketId, String currency, String statementOpeningBalance,
            String statementClosingBalance, String ledgerOpeningBalance, String ledgerClosingBalance, String periodNetPostedAmount, String closingDifference,
            String adjustmentAmount, long periodPostingCount, long totalPostingCount, String resolution, UUID adjustmentActivityId,
            UUID supersedesReconciliationId, String adjustmentReason, OffsetDateTime openingAt, OffsetDateTime closingAt) {
        return insertRawReconciliationAt(id, ownerId, accountId, pocketId, currency, statementOpeningBalance, statementClosingBalance, ledgerOpeningBalance,
                ledgerClosingBalance, periodNetPostedAmount, closingDifference, adjustmentAmount, periodPostingCount, totalPostingCount, resolution,
                adjustmentActivityId, supersedesReconciliationId, adjustmentReason, openingAt, closingAt, "migration-reconciliation-" + id);
    }

    private UUID insertRawReconciliationAt(UUID id, UUID ownerId, UUID accountId, UUID pocketId, String currency, String statementOpeningBalance,
            String statementClosingBalance, String ledgerOpeningBalance, String ledgerClosingBalance, String periodNetPostedAmount, String closingDifference,
            String adjustmentAmount, long periodPostingCount, long totalPostingCount, String resolution, UUID adjustmentActivityId,
            UUID supersedesReconciliationId, String adjustmentReason, OffsetDateTime openingAt, OffsetDateTime closingAt, String statementReference) {
        updateCommitted(
                "INSERT INTO ledger.reconciliation" + " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code," +
                        " statement_reference, statement_opening_at, statement_closing_at, statement_opening_balance," +
                        " statement_closing_balance, ledger_opening_balance, ledger_closing_balance_before_adjustment," +
                        " period_net_posted_amount, closing_difference, adjustment_amount, period_posting_count," +
                        " total_posting_count_through_closing, resolution, adjustment_activity_id, supersedes_reconciliation_id," +
                        " source_kind, adjustment_reason, created_at)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?::numeric," +
                        " ?::numeric, ?::numeric, ?::numeric, ?, ?, ?, ?, ?, 'USER_ENTERED', ?, ?)",
                id, ownerId, accountId, pocketId, currency, statementReference, openingAt, closingAt, statementOpeningBalance, statementClosingBalance,
                ledgerOpeningBalance, ledgerClosingBalance, periodNetPostedAmount, closingDifference, adjustmentAmount, periodPostingCount, totalPostingCount,
                resolution, adjustmentActivityId, supersedesReconciliationId, adjustmentReason, closingAt);
        return id;
    }

    private UUID insertRawBalancedReconciliationAt(UUID ownerId, UUID accountId, UUID pocketId, OffsetDateTime openingAt, OffsetDateTime closingAt) {
        return insertRawReconciliationAt(UUID.randomUUID(), ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, null, null, openingAt, closingAt);
    }

    private UUID insertRawReconciliationWithReference(UUID ownerId, UUID accountId, UUID pocketId, String statementReference) {
        var closingAt = timestamp();
        return insertRawReconciliationAt(UUID.randomUUID(), ownerId, accountId, pocketId, "USD", "100", "100", "100", "100", "0", "0", null, 0, 1, "BALANCED",
                null, null, null, closingAt.minusMinutes(1), closingAt, statementReference);
    }

    private void insertRawReversal(UUID ownerId, UUID originalActivityId) {
        var id = UUID.randomUUID();
        updateCommitted("INSERT INTO ledger.activity" + " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence," +
                " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision," + " correction_reason, reverses_activity_id)" +
                " VALUES (?, ?, ?, ?, 0, 'REVERSAL', 'HISTORICAL_FACT', ?, ?, 'USER_ENTERED'," + " 'NOT_APPLICABLE', 'migration reversal', ?)", id, ownerId,
                UUID.randomUUID(), "migration.reversal." + id, timestamp(), timestamp(), originalActivityId);
    }

    private void insertRawIdempotency(UUID ownerId, String scope, UUID requestId, String snapshot) {
        updateCommitted(
                "INSERT INTO ledger.idempotency_record" + " (id, owner_user_account_id, operation_scope, client_request_id, request_hash," +
                        " result_resource_kind, result_resource_id, result_snapshot, created_at)" + " VALUES (?, ?, ?, ?, ?, 'ACCOUNT', ?, ?::jsonb, ?)",
                UUID.randomUUID(), ownerId, scope, requestId, "a".repeat(64), UUID.randomUUID(), snapshot, timestamp());
    }

    private void updateCommitted(String sql, Object... arguments) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(sql, arguments));
    }

    private static OffsetDateTime timestamp() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private void assertNumericColumn(String table, String column) {
        var shape = jdbcTemplate.queryForMap("SELECT numeric_precision, numeric_scale FROM information_schema.columns" +
                " WHERE table_schema = 'ledger' AND table_name = ? AND column_name = ?", table, column);
        assertThat(shape).containsEntry("numeric_precision", 38).containsEntry("numeric_scale", 18);
    }
}
