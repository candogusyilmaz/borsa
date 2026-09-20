package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class PortfolioMigrationTest {

    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Set<String> LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "position_projection", "portfolio", "portfolio_account_membership", "reconciliation", "security_posting",
            "trade_import_batch", "trade_import_issue", "trade_import_row");
    private static final Set<String> V7_LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "position_projection", "portfolio", "portfolio_account_membership", "reconciliation", "security_posting");
    private static final Set<String> V6_LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "position_projection", "reconciliation", "security_posting");

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
    void freshDatabaseMigratesThroughV8AndContainsOnlyTheAuthorizedInvestingTables() {
        assertThat(flyway.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(Set.copyOf(jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                .isEqualTo(LEDGER_TABLES);

        var constraints = Set.copyOf(jdbcTemplate
                .queryForList("SELECT conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = 'ledger'", String.class));
        assertThat(constraints).contains("pk_ledger_portfolio", "fk_ledger_portfolio_owner", "uq_ledger_portfolio_owner_id", "ck_ledger_portfolio_name",
                "ck_ledger_portfolio_name_normalized", "ck_ledger_portfolio_version_non_negative", "pk_ledger_portfolio_account_membership",
                "fk_ledger_portfolio_account_membership_owner", "fk_ledger_portfolio_account_membership_portfolio",
                "fk_ledger_portfolio_account_membership_account", "uq_ledger_portfolio_account_membership");
        var indexes = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'ledger' AND indexname IN" +
                        " ('uix_ledger_portfolio_active_name', 'ix_ledger_portfolio_owner_name', 'ix_ledger_portfolio_account_membership_account')",
                String.class));
        assertThat(indexes).containsExactlyInAnyOrder("uix_ledger_portfolio_active_name", "ix_ledger_portfolio_owner_name",
                "ix_ledger_portfolio_account_membership_account");
    }

    @Test
    void v6UpgradePreservesAccountsCashSecurityReconciliationAndPositionRows() throws Exception {
        var databaseName = "portfolio_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var adminUrl = postgres.getJdbcUrl();
        var targetUrl = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + databaseName;
        try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE " + databaseName);
        }

        try {
            var v6 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("6")).load();
            v6.migrate();
            assertThat(v6.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6");

            var dataSource = new DriverManagerDataSource(targetUrl, postgres.getUsername(), postgres.getPassword());
            var v6Jdbc = new JdbcTemplate(dataSource);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            var fixture = seedV6Fixture(v6Jdbc, transaction);
            assertThat(Set.copyOf(v6Jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                    .isEqualTo(V6_LEDGER_TABLES);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.money_posting", Integer.class)).isGreaterThan(0);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.security_posting", Integer.class)).isEqualTo(3);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation", Integer.class)).isEqualTo(1);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.position_projection", Integer.class)).isEqualTo(2);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE current_quantity > 0", Integer.class)).isEqualTo(1);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE current_quantity = 0", Integer.class)).isEqualTo(1);
            assertThat(v6Jdbc.queryForObject("SELECT COUNT(*) FROM reference.instrument WHERE id IN (?, ?)", Integer.class, fixture.ownerInstrumentId(),
                    fixture.globalInstrumentId())).isEqualTo(2);
            var before = snapshotV6Rows(v6Jdbc);

            var v7 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("7")).load();
            v7.migrate();
            assertThat(v7.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6", "7");
            assertThat(snapshotV6Rows(v6Jdbc)).isEqualTo(before);
            assertThat(Set.copyOf(v6Jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                    .isEqualTo(V7_LEDGER_TABLES);
        } finally {
            try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
                admin.createStatement().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
            }
        }
    }

    @Test
    void portfolioTablesEnforceNameMembershipOwnerAndCleanupConstraints() {
        var ownerId = insertUser(jdbcTemplate, "portfolio-migration-owner");
        var otherOwnerId = insertUser(jdbcTemplate, "portfolio-migration-other");
        var accountId = insertAccount(jdbcTemplate, ownerId, "Migration account", false);
        var archivedAccountId = insertAccount(jdbcTemplate, ownerId, "Archived account", true);
        var foreignAccountId = insertAccount(jdbcTemplate, otherOwnerId, "Foreign account", false);
        var activeId = insertPortfolio(jdbcTemplate, ownerId, "Reusable", "REUSABLE", null, 0);
        var archivedId = insertPortfolio(jdbcTemplate, ownerId, "Archived", "ARCHIVED", OffsetDateTime.now(ZoneOffset.UTC), 0);

        assertThatThrownBy(() -> insertPortfolio(jdbcTemplate, ownerId, " Bad ", "BAD", null, 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPortfolio(jdbcTemplate, ownerId, "Mismatch", "OTHER", null, 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPortfolio(jdbcTemplate, ownerId, "x".repeat(161), "X".repeat(161), null, 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPortfolio(jdbcTemplate, ownerId, "Negative version", "NEGATIVE VERSION", null, -1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPortfolio(jdbcTemplate, ownerId, "reusable", "REUSABLE", null, 0)).isInstanceOf(DataAccessException.class);

        var reused = insertPortfolio(jdbcTemplate, ownerId, "Archived", "ARCHIVED", null, 0);
        assertThat(reused).isNotEqualTo(archivedId);

        var firstMembership = insertMembership(jdbcTemplate, ownerId, activeId, accountId);
        assertThatThrownBy(() -> insertMembership(jdbcTemplate, ownerId, activeId, accountId)).isInstanceOf(DataAccessException.class);
        assertThat(insertMembership(jdbcTemplate, ownerId, activeId, archivedAccountId)).isNotEqualTo(firstMembership);
        assertThatThrownBy(() -> insertMembership(jdbcTemplate, ownerId, archivedId, foreignAccountId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMembership(jdbcTemplate, ownerId, UUID.randomUUID(), accountId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMembership(jdbcTemplate, ownerId, activeId, UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.portfolio_account_membership WHERE financial_account_id = ?", Integer.class,
                archivedAccountId)).isEqualTo(1);

        updateCommitted("DELETE FROM identity.user_account WHERE id = ?", ownerId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.portfolio WHERE owner_user_account_id = ?", Integer.class, ownerId)).isZero();
        assertThat(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.portfolio_account_membership WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
    }

    static Map<String, String> snapshotV6Rows(JdbcTemplate jdbcTemplate) {
        var snapshots = new LinkedHashMap<String, String>();
        for (var table : V6_LEDGER_TABLES) {
            snapshots.put("ledger." + table, snapshot(jdbcTemplate, "ledger", table));
        }
        snapshots.put("reference.instrument", snapshot(jdbcTemplate, "reference", "instrument"));
        snapshots.put("identity.user_account", snapshot(jdbcTemplate, "identity", "user_account"));
        return Map.copyOf(snapshots);
    }

    private static String snapshot(JdbcTemplate jdbcTemplate, String schema, String table) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(string_agg(to_jsonb(snapshot_rows)::text, E'\\n' ORDER BY snapshot_rows.id), '')" +
                " FROM (SELECT * FROM " + schema + "." + table + ") snapshot_rows", String.class);
    }

    static V6Fixture seedV6Fixture(JdbcTemplate jdbcTemplate, TransactionTemplate transaction) {
        var ownerId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var pocketId = UUID.randomUUID();
        var base = OffsetDateTime.parse("2026-09-18T09:00:00Z");
        var recordedAt = base.plusHours(1);

        transaction.executeWithoutResult(status -> {
            var email = ownerId + "@portfolio-upgrade.test";
            jdbcTemplate.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", ownerId,
                    email, email, recordedAt, recordedAt);
        });
        var ownerInstrumentId = insertInstrument(jdbcTemplate, ownerId, "PORT-V6-OWNER-" + UUID.randomUUID().toString().substring(0, 8));
        var globalInstrumentId = insertInstrument(jdbcTemplate, null, "PORT-V6-GLOBAL-" + UUID.randomUUID().toString().substring(0, 8));

        transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO ledger.financial_account" +
                    " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode, negative_balance_policy, currency_code, time_zone," +
                    " created_at, updated_at, version) VALUES (?, ?, 'V6 Brokerage', 'V6 BROKERAGE', 'BROKERAGE', 'FULL_LEDGER', 'HARD_FLOOR'," +
                    " 'USD', 'UTC', ?, ?, 0)", accountId, ownerId, base, recordedAt);
            jdbcTemplate.update("INSERT INTO ledger.account_cash_pocket" +
                    " (id, owner_user_account_id, financial_account_id, currency_code, coverage_status, coverage_from, created_at, updated_at, version)" +
                    " VALUES (?, ?, ?, 'USD', 'KNOWN_FROM_OPENING', ?, ?, ?, 0)", pocketId, ownerId, accountId, base, base, recordedAt);

            var openingActivityId = insertActivity(jdbcTemplate, ownerId, "OPENING_BALANCE", "HISTORICAL_FACT", base, null);
            jdbcTemplate.update("UPDATE ledger.financial_account SET current_opening_activity_id = ? WHERE id = ?", openingActivityId, accountId);
            insertMoneyPosting(jdbcTemplate, ownerId, openingActivityId, accountId, pocketId, "100", "OPENING", recordedAt);
            jdbcTemplate.update(
                    "INSERT INTO ledger.account_balance_projection" +
                            " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code, ledger_balance, last_applied_recorded_at," +
                            " last_applied_activity_id, updated_at, version) VALUES (?, ?, ?, ?, 'USD', 100, ?, ?, ?, 0)",
                    UUID.randomUUID(), ownerId, accountId, pocketId, recordedAt, openingActivityId, recordedAt);
            jdbcTemplate.update("INSERT INTO ledger.reconciliation" +
                    " (id, owner_user_account_id, financial_account_id, cash_pocket_id, currency_code, statement_reference, statement_opening_at," +
                    " statement_closing_at, statement_opening_balance, statement_closing_balance, ledger_opening_balance," +
                    " ledger_closing_balance_before_adjustment, period_net_posted_amount, closing_difference, adjustment_amount, period_posting_count," +
                    " total_posting_count_through_closing, resolution, adjustment_activity_id, supersedes_reconciliation_id, source_kind, adjustment_reason," +
                    " created_at) VALUES (?, ?, ?, ?, 'USD', 'V6 upgrade statement', ?, ?, 100, 100, 100, 100, 0, 0, NULL, 0, 1," +
                    " 'BALANCED', NULL, NULL, 'USER_ENTERED', NULL, ?)", UUID.randomUUID(), ownerId, accountId, pocketId, base.minusMinutes(1),
                    base.plusMinutes(1), recordedAt);

            var openTradeAt = base.plusMinutes(10);
            var openActivityId = insertTrade(jdbcTemplate, ownerId, accountId, pocketId, ownerInstrumentId, "BUY", "1", openTradeAt, recordedAt);
            var closeBuyAt = base.plusMinutes(20);
            insertTrade(jdbcTemplate, ownerId, accountId, pocketId, globalInstrumentId, "BUY", "1", closeBuyAt, recordedAt);
            var closeSellAt = base.plusMinutes(30);
            var closeActivityId = insertTrade(jdbcTemplate, ownerId, accountId, pocketId, globalInstrumentId, "SELL", "1", closeSellAt, recordedAt);
            insertPositionProjection(jdbcTemplate, ownerId, accountId, ownerInstrumentId, "1", "10", openTradeAt, openActivityId, recordedAt);
            insertPositionProjection(jdbcTemplate, ownerId, accountId, globalInstrumentId, "0", "0", closeSellAt, closeActivityId, recordedAt);
        });

        return new V6Fixture(ownerId, accountId, ownerInstrumentId, globalInstrumentId);
    }

    private static UUID insertActivity(JdbcTemplate jdbcTemplate, UUID ownerId, String type, String recordingMode, OffsetDateTime effectiveAt,
            Long economicSequence) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO ledger.activity" +
                        " (id, owner_user_account_id, client_event_id, operation_scope, command_sequence, correction_reason, activity_type, recording_mode," +
                        " effective_at, recorded_at, source_kind, policy_decision, economic_sequence)" +
                        " VALUES (?, ?, ?, ?, 0, NULL, ?, ?, ?, ?, 'USER_ENTERED', 'ALLOWED', ?)",
                id, ownerId, UUID.randomUUID(), "portfolio.migration." + id, type, recordingMode, effectiveAt, effectiveAt.plusMinutes(1), economicSequence);
        return id;
    }

    private static UUID insertTrade(JdbcTemplate jdbcTemplate, UUID ownerId, UUID accountId, UUID pocketId, UUID instrumentId, String side, String quantity,
            OffsetDateTime effectiveAt, OffsetDateTime recordedAt) {
        var isBuy = side.equals("BUY");
        var activityId = insertActivity(jdbcTemplate, ownerId, isBuy ? "SECURITY_BUY" : "SECURITY_SELL", "CURRENT_ACTION", effectiveAt, 0L);
        insertMoneyPosting(jdbcTemplate, ownerId, activityId, accountId, pocketId, isBuy ? "-10" : "10", isBuy ? "TRADE_PURCHASE" : "TRADE_PROCEEDS",
                recordedAt);
        jdbcTemplate.update("INSERT INTO ledger.security_posting" +
                " (id, owner_user_account_id, activity_id, financial_account_id, instrument_id, trade_currency_code, quantity_delta, unit_price, gross_amount," +
                " posting_role, effective_at, economic_sequence, reverses_security_posting_id, created_at)" +
                " VALUES (?, ?, ?, ?, ?, 'USD', ?::numeric, 10, 10, ?, ?, 0, NULL, ?)", UUID.randomUUID(), ownerId, activityId, accountId, instrumentId,
                isBuy ? quantity : "-" + quantity, side, effectiveAt, recordedAt);
        return activityId;
    }

    private static void insertMoneyPosting(JdbcTemplate jdbcTemplate, UUID ownerId, UUID activityId, UUID accountId, UUID pocketId, String amount, String role,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO ledger.money_posting" +
                        " (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id, currency_code, amount, posting_role, created_at)" +
                        " VALUES (?, ?, ?, ?, ?, 'USD', ?::numeric, ?, ?)",
                UUID.randomUUID(), ownerId, activityId, accountId, pocketId, amount, role, createdAt);
    }

    private static void insertPositionProjection(JdbcTemplate jdbcTemplate, UUID ownerId, UUID accountId, UUID instrumentId, String quantity,
            String remainingBasis, OffsetDateTime asOf, UUID watermarkActivityId, OffsetDateTime recordedAt) {
        jdbcTemplate.update(
                "INSERT INTO ledger.position_projection" +
                        " (id, owner_user_account_id, financial_account_id, instrument_id, currency_code, current_quantity, remaining_economic_basis," +
                        " cumulative_realized_economic_pnl, calculation_policy, projection_status, as_of, input_watermark_activity_id," +
                        " last_successful_build_at, stale_from, updated_at, version)" +
                        " VALUES (?, ?, ?, ?, 'USD', ?::numeric, ?::numeric, 0, 'WEIGHTED_AVERAGE_ECONOMIC_V1', 'CURRENT', ?, ?, ?, NULL, ?, 0)",
                UUID.randomUUID(), ownerId, accountId, instrumentId, quantity, remainingBasis, asOf, watermarkActivityId, recordedAt, recordedAt);
    }

    private static UUID insertInstrument(JdbcTemplate jdbcTemplate, UUID ownerId, String symbol) {
        var id = UUID.randomUUID();
        var name = "Portfolio migration " + symbol;
        var now = OffsetDateTime.parse("2026-09-18T09:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO reference.instrument" +
                        " (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized, instrument_type, quotation_currency_code," +
                        " valuation_method, active, source_kind, version, created_at, updated_at)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, 'EQUITY', 'USD', 'NOT_VALUED', true, ?, 0, ?, ?)",
                id, ownerId, MANUAL_MARKET_ID, symbol, symbol.toUpperCase(java.util.Locale.ROOT), name, name.toUpperCase(java.util.Locale.ROOT),
                ownerId == null ? "REFERENCE_SEED" : "USER_ENTERED", now, now);
        return id;
    }

    private UUID insertUser(JdbcTemplate jdbcTemplate, String label) {
        var id = UUID.randomUUID();
        var email = label + "+" + id + "@portfolio-migration.test";
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        updateCommitted("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", id, email, email, now,
                now);
        return id;
    }

    private UUID insertAccount(JdbcTemplate jdbcTemplate, UUID ownerId, String name, boolean archived) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        updateCommitted(
                "INSERT INTO ledger.financial_account" +
                        " (id, owner_user_account_id, name, name_normalized, account_kind, tracking_mode, negative_balance_policy, currency_code, time_zone," +
                        " archived_at, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'BROKERAGE', 'HOLDINGS_ONLY', NULL, 'USD', 'UTC', ?, ?, ?, 0)",
                id, ownerId, name, name.toUpperCase(java.util.Locale.ROOT), archived ? now : null, now, now);
        return id;
    }

    private UUID insertPortfolio(JdbcTemplate jdbcTemplate, UUID ownerId, String name, String normalizedName, OffsetDateTime archivedAt, long version) {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        updateCommitted(
                "INSERT INTO ledger.portfolio" +
                        " (id, owner_user_account_id, name, name_normalized, archived_at, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, ownerId, name, normalizedName, archivedAt, now, now, version);
        return id;
    }

    private UUID insertMembership(JdbcTemplate jdbcTemplate, UUID ownerId, UUID portfolioId, UUID accountId) {
        var id = UUID.randomUUID();
        updateCommitted(
                "INSERT INTO ledger.portfolio_account_membership" +
                        " (id, owner_user_account_id, portfolio_id, financial_account_id, created_at) VALUES (?, ?, ?, ?, ?)",
                id, ownerId, portfolioId, accountId, OffsetDateTime.now(ZoneOffset.UTC));
        return id;
    }

    private void updateCommitted(String sql, Object... arguments) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update(sql, arguments));
    }

    record V6Fixture(UUID ownerId, UUID accountId, UUID ownerInstrumentId, UUID globalInstrumentId) {}
}
