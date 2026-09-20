package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TradeImportMigrationTest {

    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    private static final Set<String> V8_LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "position_projection", "portfolio", "portfolio_account_membership", "reconciliation", "security_posting",
            "trade_import_batch", "trade_import_issue", "trade_import_row");
    private static final Set<String> V7_LEDGER_TABLES = Set.of("account_balance_projection", "account_cash_pocket", "activity", "financial_account",
            "idempotency_record", "money_posting", "position_projection", "portfolio", "portfolio_account_membership", "reconciliation", "security_posting");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void emptyDatabaseMigratesThroughV8WithHibernateMappingsValidated() {
        assertThat(flyway.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(Set.copyOf(jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                .isEqualTo(V8_LEDGER_TABLES);
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger' AND table_name LIKE 'trade_import_%'", String.class))
                .containsExactlyInAnyOrder("trade_import_batch", "trade_import_issue", "trade_import_row");
    }

    @Test
    void v7UpgradePreservesManualAndReversedTradesProjectionsReconciliationPortfolioAndInstruments() throws Exception {
        var databaseName = "trade_import_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var adminUrl = postgres.getJdbcUrl();
        var targetUrl = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + databaseName;
        try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE " + databaseName);
        }

        try {
            var v6 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("6")).load();
            v6.migrate();
            var dataSource = new DriverManagerDataSource(targetUrl, postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            var fixture = PortfolioMigrationTest.seedV6Fixture(jdbc, transaction);

            var v7 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("7")).load();
            v7.migrate();
            assertThat(v7.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6", "7");
            addPortfolioMembership(jdbc, fixture);
            addReversedManualTrade(jdbc, transaction, fixture);
            assertV7Fixture(jdbc, fixture);

            var before = snapshotV7Rows(jdbc);
            var v8 = Flyway.configure().dataSource(targetUrl, postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("8")).load();
            v8.migrate();
            assertThat(v8.info().applied()).extracting(migration -> migration.getVersion().toString()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
            assertThat(Set.copyOf(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                    .isEqualTo(V8_LEDGER_TABLES);
            assertThat(snapshotV7Rows(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE source_kind <> 'USER_ENTERED' OR source_import_row_id IS NOT NULL",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_row", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_issue", Integer.class)).isZero();
        } finally {
            try (var admin = DriverManager.getConnection(adminUrl, postgres.getUsername(), postgres.getPassword())) {
                admin.createStatement().execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
            }
        }
    }

    @Test
    void importTablesEnforceOwnerLifecycleAndShapeConstraintsAndCleanUpWithTheirOwner() {
        var createdAt = OffsetDateTime.parse("2026-09-20T12:00:00Z");
        var ownerId = insertUserAccount(jdbcTemplate, createdAt);
        var accountId = insertHoldingsAccount(jdbcTemplate, ownerId, createdAt);
        var foreignOwnerId = insertUserAccount(jdbcTemplate, createdAt.plusSeconds(1));
        var foreignAccountId = insertHoldingsAccount(jdbcTemplate, foreignOwnerId, createdAt.plusSeconds(1));
        var contentHash = "a".repeat(64);
        var batchId = UUID.randomUUID();
        var rowId = UUID.randomUUID();
        inTransaction(() -> {
            insertBatch(jdbcTemplate, batchId, ownerId, accountId, contentHash, "PARSED", null, 0, createdAt);
            insertInvalidRow(jdbcTemplate, rowId, ownerId, batchId, 1, "[\"bad\"]", null, createdAt);
            insertIssue(jdbcTemplate, ownerId, batchId, rowId, "RECORD_SHAPE_INVALID", "row", createdAt);
        });

        assertThatThrownBy(() -> inTransaction(
                () -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "e".repeat(64), "UNKNOWN", "import.csv", "text/csv", 8, 1, createdAt, null, 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "f".repeat(64), "COMMITTED", "import.csv",
                "text/csv", 8, 1, createdAt, createdAt.minusSeconds(1), 0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "g".repeat(64), "PARSED", " ", "text/csv", 8, 1, createdAt, null, 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "h".repeat(64), "PARSED", "f".repeat(256),
                "text/csv", 8, 1, createdAt, null, 0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "i".repeat(64), "PARSED", "import.csv",
                "t".repeat(121), 8, 1, createdAt, null, 0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "j".repeat(64), "PARSED", "import.csv", "text/csv", 0, 1, createdAt, null, 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "k".repeat(64), "PARSED", "import.csv",
                "text/csv", 1_048_577L, 1, createdAt, null, 0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "G".repeat(64), "PARSED", "import.csv", "text/csv", 8, 1, createdAt, null, 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "l".repeat(64), "PARSED", "import.csv", "text/csv", 8, 0, createdAt, null, 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatchWithMetadata(jdbcTemplate, ownerId, accountId, "m".repeat(64), "PARSED", "import.csv",
                "text/csv", 8, 501, createdAt, null, 0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatch(jdbcTemplate, ownerId, accountId, contentHash, "PARSED", null, 0, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatch(jdbcTemplate, ownerId, accountId, "b".repeat(64), "COMMITTED", null, 0, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatch(jdbcTemplate, ownerId, accountId, "c".repeat(64), "PARSED", null, -1, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertBatch(jdbcTemplate, ownerId, foreignAccountId, "d".repeat(64), "PARSED", null, 0, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 2, "{}", null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 0, "[\"bad\"]", null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 501, "[\"bad\"]", null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        var oversizedSourceValues = "[\"" + "x".repeat(65_535) + "\"]";
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 2, oversizedSourceValues, null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 2, "[\"bad\"]", "BUY", createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, foreignOwnerId, batchId, 2, "[\"bad\"]", null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertInvalidRow(jdbcTemplate, ownerId, batchId, 1, "[\"bad\"]", null, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertIssue(jdbcTemplate, ownerId, batchId, rowId, "UNSAFE_CODE", "row", createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertIssue(jdbcTemplate, ownerId, batchId, rowId, "RECORD_SHAPE_INVALID", " row ", createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertIssue(jdbcTemplate, ownerId, batchId, rowId, "RECORD_SHAPE_INVALID", "row", createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertImportedActivityWithoutSourceRow(jdbcTemplate, ownerId, createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertActivityWithSourceRow(jdbcTemplate, foreignOwnerId, UUID.randomUUID(), rowId, "FILE_IMPORTED",
                "SECURITY_BUY", "HISTORICAL_FACT", createdAt))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertActivityWithSourceRow(jdbcTemplate, ownerId, UUID.randomUUID(), rowId, "USER_ENTERED",
                "SECURITY_BUY", "HISTORICAL_FACT", createdAt))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> insertActivityWithSourceRow(jdbcTemplate, ownerId, UUID.randomUUID(), rowId, "FILE_IMPORTED",
                "CASH_DEPOSIT", "HISTORICAL_FACT", createdAt))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertActivityWithSourceRow(jdbcTemplate, ownerId, UUID.randomUUID(), rowId, "FILE_IMPORTED", "SECURITY_BUY", "CURRENT", createdAt)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(
                () -> insertActivityWithSourceRow(jdbcTemplate, ownerId, UUID.randomUUID(), rowId, "UNKNOWN", "SECURITY_BUY", "HISTORICAL_FACT", createdAt)))
                .isInstanceOf(DataAccessException.class);

        inTransaction(() -> jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", ownerId));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_batch WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_row WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.trade_import_issue WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger.financial_account WHERE owner_user_account_id = ?", Integer.class, ownerId))
                .isZero();
    }

    @Test
    void validNormalizedRowIsAcceptedAndRawSqlEnforcesTradeSignsAndEconomicShape() {
        var createdAt = OffsetDateTime.parse("2026-09-20T12:00:00Z");
        var effectiveAt = createdAt.minusHours(2);
        var ownerId = insertUserAccount(jdbcTemplate, createdAt);
        var accountId = insertHoldingsAccount(jdbcTemplate, ownerId, createdAt);
        var instrumentId = insertImportInstrument(ownerId, createdAt);
        var batchId = UUID.randomUUID();
        var rowId = UUID.randomUUID();
        inTransaction(() -> {
            insertBatch(jdbcTemplate, batchId, ownerId, accountId, "a".repeat(64), "PARSED", null, 0, createdAt);
            insertValidNormalizedRow(jdbcTemplate, rowId, ownerId, batchId, instrumentId, effectiveAt, createdAt);
        });

        assertThat(jdbcTemplate.queryForObject("SELECT normalization_status FROM ledger.trade_import_row WHERE id = ?", String.class, rowId))
                .isEqualTo("VALID");
        assertThat(jdbcTemplate.queryForObject("SELECT row_fingerprint = repeat('a', 64) AND cash_delta = -10" +
                " AND source_external_id = 'source-1' FROM ledger.trade_import_row WHERE id = ?", Boolean.class, rowId)).isTrue();

        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET cash_delta = 10 WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET side = 'SELL' WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                () -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET row_fingerprint = repeat('A', 64) WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET row_fingerprint = NULL WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                () -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET source_external_id = ' source-1 ' WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET source_external_id = NULL WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET quantity = 0 WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET unit_price = 0 WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET commission_amount = -1 WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update("UPDATE ledger.trade_import_row SET gross_amount = NULL WHERE id = ?", rowId)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertUserAccount(JdbcTemplate jdbc, OffsetDateTime createdAt) {
        var ownerId = UUID.randomUUID();
        var email = ownerId + "@trade-import-migration.test";
        inTransaction(() -> jdbc.update("INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ownerId, email, email, createdAt, createdAt));
        return ownerId;
    }

    private UUID insertHoldingsAccount(JdbcTemplate jdbc, UUID ownerId, OffsetDateTime createdAt) {
        var accountId = UUID.randomUUID();
        var name = "Import test " + accountId.toString().substring(0, 8);
        inTransaction(() -> jdbc.update(
                "INSERT INTO ledger.financial_account (id, owner_user_account_id, name, name_normalized, account_kind," +
                        " tracking_mode, negative_balance_policy, currency_code, time_zone, created_at, updated_at, version)" +
                        " VALUES (?, ?, ?, ?, 'BROKERAGE', 'HOLDINGS_ONLY', NULL, 'USD', 'UTC', ?, ?, 0)",
                accountId, ownerId, name, name.toUpperCase(java.util.Locale.ROOT), createdAt, createdAt));
        return accountId;
    }

    private UUID insertImportInstrument(UUID ownerId, OffsetDateTime createdAt) {
        var instrumentId = UUID.randomUUID();
        var symbol = "IMP-" + instrumentId.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        var name = "Import migration " + symbol;
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized," +
                        " instrument_type, quotation_currency_code, valuation_method, active, source_kind, version, created_at, updated_at)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, 'EQUITY', 'USD', 'NOT_VALUED', TRUE, 'USER_ENTERED', 0, ?, ?)",
                instrumentId, ownerId, MANUAL_MARKET_ID, symbol, symbol, name, name.toUpperCase(java.util.Locale.ROOT), createdAt, createdAt));
        return instrumentId;
    }

    private static UUID insertBatch(JdbcTemplate jdbc, UUID ownerId, UUID accountId, String contentHash, String status, OffsetDateTime committedAt,
            long version, OffsetDateTime createdAt) {
        var batchId = UUID.randomUUID();
        insertBatch(jdbc, batchId, ownerId, accountId, contentHash, status, committedAt, version, createdAt);
        return batchId;
    }

    private static void insertBatch(JdbcTemplate jdbc, UUID batchId, UUID ownerId, UUID accountId, String contentHash, String status,
            OffsetDateTime committedAt, long version, OffsetDateTime createdAt) {
        insertBatchWithMetadata(jdbc, batchId, ownerId, accountId, contentHash, status, "import.csv", "text/csv", 8, 1, createdAt, committedAt, version);
    }

    private static void insertBatchWithMetadata(JdbcTemplate jdbc, UUID ownerId, UUID accountId, String contentHash, String status, String fileName,
            String mediaType, long byteSize, int parsedRowCount, OffsetDateTime createdAt, OffsetDateTime committedAt, long version) {
        insertBatchWithMetadata(jdbc, UUID.randomUUID(), ownerId, accountId, contentHash, status, fileName, mediaType, byteSize, parsedRowCount, createdAt,
                committedAt, version);
    }

    private static void insertBatchWithMetadata(JdbcTemplate jdbc, UUID batchId, UUID ownerId, UUID accountId, String contentHash, String status,
            String fileName, String mediaType, long byteSize, int parsedRowCount, OffsetDateTime createdAt, OffsetDateTime committedAt, long version) {
        jdbc.update(
                "INSERT INTO ledger.trade_import_batch (id, owner_user_account_id, financial_account_id, import_format, status, original_file_name," +
                        " media_type, byte_size, content_sha256, parsed_row_count, created_at, committed_at, version)" +
                        " VALUES (?, ?, ?, 'FUNDED_TRADE_CSV_V1', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchId, ownerId, accountId, status, fileName, mediaType, byteSize, contentHash, parsedRowCount, createdAt, committedAt, version);
    }

    private static UUID insertInvalidRow(JdbcTemplate jdbc, UUID ownerId, UUID batchId, int sourceRecordNumber, String sourceValues, String side,
            OffsetDateTime createdAt) {
        var rowId = UUID.randomUUID();
        insertInvalidRow(jdbc, rowId, ownerId, batchId, sourceRecordNumber, sourceValues, side, createdAt);
        return rowId;
    }

    private static void insertInvalidRow(JdbcTemplate jdbc, UUID rowId, UUID ownerId, UUID batchId, int sourceRecordNumber, String sourceValues, String side,
            OffsetDateTime createdAt) {
        jdbc.update(
                "INSERT INTO ledger.trade_import_row (id, owner_user_account_id, import_batch_id, source_record_number, source_values," +
                        " normalization_status, side, created_at) VALUES (?, ?, ?, ?, ?::jsonb, 'INVALID', ?, ?)",
                rowId, ownerId, batchId, sourceRecordNumber, sourceValues, side, createdAt);
    }

    private static void insertValidNormalizedRow(JdbcTemplate jdbc, UUID rowId, UUID ownerId, UUID batchId, UUID instrumentId, OffsetDateTime effectiveAt,
            OffsetDateTime createdAt) {
        var sourceValues = "[\"source-1\",\"BUY\",\"" + instrumentId + "\",\"USD\",\"" + effectiveAt + "\",\"1\",\"1\",\"10\",\"0\"]";
        jdbc.update(
                "INSERT INTO ledger.trade_import_row (id, owner_user_account_id, import_batch_id, source_record_number, source_external_id," +
                        " source_values, normalization_status, side, instrument_id, currency_code, effective_at, economic_sequence, quantity, unit_price," +
                        " commission_amount, gross_amount, cash_delta, row_fingerprint, created_at)" +
                        " VALUES (?, ?, ?, 1, 'source-1', ?::jsonb, 'VALID', 'BUY', ?, 'USD', ?, 1, 1, 10, 0, 10, -10, ?, ?)",
                rowId, ownerId, batchId, sourceValues, instrumentId, effectiveAt, "a".repeat(64), createdAt);
    }

    private static void insertIssue(JdbcTemplate jdbc, UUID ownerId, UUID batchId, UUID rowId, String code, String field, OffsetDateTime createdAt) {
        jdbc.update("INSERT INTO ledger.trade_import_issue (id, owner_user_account_id, import_batch_id, import_row_id, issue_code, field_name, created_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), ownerId, batchId, rowId, code, field, createdAt);
    }

    private static void insertImportedActivityWithoutSourceRow(JdbcTemplate jdbc, UUID ownerId, OffsetDateTime createdAt) {
        insertActivityWithSourceRow(jdbc, ownerId, UUID.randomUUID(), null, "FILE_IMPORTED", "SECURITY_BUY", "HISTORICAL_FACT", createdAt);
    }

    private static void insertActivityWithSourceRow(JdbcTemplate jdbc, UUID ownerId, UUID activityId, UUID rowId, String sourceKind, String activityType,
            String recordingMode, OffsetDateTime createdAt) {
        jdbc.update(
                "INSERT INTO ledger.activity (id, owner_user_account_id, client_event_id, operation_scope, command_sequence, correction_reason," +
                        " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision, economic_sequence, source_import_row_id)" +
                        " VALUES (?, ?, ?, 'migration.import-shape', 0, NULL, ?, ?, ?, ?, ?, 'ALLOWED', 0, ?)",
                activityId, ownerId, UUID.randomUUID(), activityType, recordingMode, createdAt, createdAt, sourceKind, rowId);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private static void addPortfolioMembership(JdbcTemplate jdbc, PortfolioMigrationTest.V6Fixture fixture) {
        var portfolioId = UUID.randomUUID();
        var membershipId = UUID.randomUUID();
        var createdAt = OffsetDateTime.parse("2026-09-18T11:00:00Z");
        jdbc.update("INSERT INTO ledger.portfolio (id, owner_user_account_id, name, name_normalized, created_at, updated_at, version)" +
                " VALUES (?, ?, 'Migration portfolio', 'MIGRATION PORTFOLIO', ?, ?, 0)", portfolioId, fixture.ownerId(), createdAt, createdAt);
        jdbc.update("INSERT INTO ledger.portfolio_account_membership (id, owner_user_account_id, portfolio_id, financial_account_id, created_at)" +
                " VALUES (?, ?, ?, ?, ?)", membershipId, fixture.ownerId(), portfolioId, fixture.accountId(), createdAt);
    }

    private static void addReversedManualTrade(JdbcTemplate jdbc, TransactionTemplate transaction, PortfolioMigrationTest.V6Fixture fixture) {
        var cashPocketId = jdbc.queryForObject("SELECT id FROM ledger.account_cash_pocket WHERE financial_account_id = ?", UUID.class, fixture.accountId());
        var effectiveAt = OffsetDateTime.parse("2026-09-18T10:00:00Z");
        var recordedAt = effectiveAt.plusHours(1);
        var clientEventId = UUID.randomUUID();
        var operationScope = "trade-import-migration." + UUID.randomUUID();
        var originalActivityId = UUID.randomUUID();
        var originalMoneyPostingId = UUID.randomUUID();
        var originalSecurityPostingId = UUID.randomUUID();
        var reversalActivityId = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO ledger.activity (id, owner_user_account_id, client_event_id, operation_scope, command_sequence, correction_reason," +
                            " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision, economic_sequence)" +
                            " VALUES (?, ?, ?, ?, 0, NULL, 'SECURITY_BUY', 'CURRENT_ACTION', ?, ?, 'USER_ENTERED', 'ALLOWED', 0)",
                    originalActivityId, fixture.ownerId(), clientEventId, operationScope, effectiveAt, recordedAt);
            jdbc.update(
                    "INSERT INTO ledger.money_posting (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id, currency_code," +
                            " amount, posting_role, created_at) VALUES (?, ?, ?, ?, ?, 'USD', -10, 'TRADE_PURCHASE', ?)",
                    originalMoneyPostingId, fixture.ownerId(), originalActivityId, fixture.accountId(), cashPocketId, recordedAt);
            jdbc.update(
                    "INSERT INTO ledger.security_posting (id, owner_user_account_id, activity_id, financial_account_id, instrument_id," +
                            " trade_currency_code, quantity_delta, unit_price, gross_amount, posting_role, effective_at, economic_sequence," +
                            " reverses_security_posting_id, created_at) VALUES (?, ?, ?, ?, ?, 'USD', 1, 10, 10, 'BUY', ?, 0, NULL, ?)",
                    originalSecurityPostingId, fixture.ownerId(), originalActivityId, fixture.accountId(), fixture.globalInstrumentId(), effectiveAt,
                    recordedAt);

            jdbc.update("INSERT INTO ledger.activity (id, owner_user_account_id, client_event_id, operation_scope, command_sequence, correction_reason," +
                    " activity_type, recording_mode, effective_at, recorded_at, source_kind, policy_decision, economic_sequence, reverses_activity_id)" +
                    " VALUES (?, ?, ?, ?, 0, 'Migration fixture reversal', 'REVERSAL', 'HISTORICAL_FACT', ?, ?, 'USER_ENTERED'," + " 'NOT_APPLICABLE', 0, ?)",
                    reversalActivityId, fixture.ownerId(), UUID.randomUUID(), operationScope, effectiveAt, recordedAt, originalActivityId);
            jdbc.update(
                    "INSERT INTO ledger.money_posting (id, owner_user_account_id, activity_id, financial_account_id, cash_pocket_id, currency_code," +
                            " amount, posting_role, created_at, reverses_money_posting_id) VALUES (?, ?, ?, ?, ?, 'USD', 10, 'REVERSAL', ?, ?)",
                    UUID.randomUUID(), fixture.ownerId(), reversalActivityId, fixture.accountId(), cashPocketId, recordedAt, originalMoneyPostingId);
            jdbc.update(
                    "INSERT INTO ledger.security_posting (id, owner_user_account_id, activity_id, financial_account_id, instrument_id," +
                            " trade_currency_code, quantity_delta, unit_price, gross_amount, posting_role, effective_at, economic_sequence," +
                            " reverses_security_posting_id, created_at) VALUES (?, ?, ?, ?, ?, 'USD', -1, NULL, NULL, 'REVERSAL', ?, 0, ?, ?)",
                    UUID.randomUUID(), fixture.ownerId(), reversalActivityId, fixture.accountId(), fixture.globalInstrumentId(), effectiveAt,
                    originalSecurityPostingId, recordedAt);
            jdbc.update(
                    "UPDATE ledger.position_projection SET as_of = ?, input_watermark_activity_id = ?, last_successful_build_at = ?, updated_at = ?" +
                            " WHERE owner_user_account_id = ? AND financial_account_id = ? AND instrument_id = ?",
                    effectiveAt, reversalActivityId, recordedAt, recordedAt, fixture.ownerId(), fixture.accountId(), fixture.globalInstrumentId());
            jdbc.update(
                    "UPDATE ledger.account_balance_projection SET last_applied_recorded_at = ?, last_applied_activity_id = ?, updated_at = ?" +
                            " WHERE owner_user_account_id = ? AND financial_account_id = ?",
                    recordedAt, reversalActivityId, recordedAt, fixture.ownerId(), fixture.accountId());
        });
    }

    private static void assertV7Fixture(JdbcTemplate jdbc, PortfolioMigrationTest.V6Fixture fixture) {
        assertThat(Set.copyOf(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'ledger'", String.class)))
                .isEqualTo(V7_LEDGER_TABLES);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.security_posting WHERE owner_user_account_id = ?", Integer.class, fixture.ownerId()))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.activity WHERE owner_user_account_id = ? AND activity_type = 'REVERSAL'", Integer.class,
                fixture.ownerId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ? AND current_quantity > 0",
                Integer.class, fixture.ownerId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.position_projection WHERE owner_user_account_id = ? AND current_quantity = 0",
                Integer.class, fixture.ownerId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.reconciliation WHERE owner_user_account_id = ?", Integer.class, fixture.ownerId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.portfolio_account_membership WHERE owner_user_account_id = ?", Integer.class,
                fixture.ownerId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM reference.instrument WHERE id IN (?, ?) AND" +
                        " ((id = ? AND owner_user_account_id = ?) OR (id = ? AND owner_user_account_id IS NULL))",
                Integer.class, fixture.ownerInstrumentId(), fixture.globalInstrumentId(), fixture.ownerInstrumentId(), fixture.ownerId(),
                fixture.globalInstrumentId())).isEqualTo(2);
    }

    private static Map<String, String> snapshotV7Rows(JdbcTemplate jdbc) {
        var snapshots = new LinkedHashMap<String, String>();
        for (var table : V7_LEDGER_TABLES) {
            if (!table.equals("activity")) {
                snapshots.put("ledger." + table, snapshot(jdbc, "ledger", table));
            }
        }
        snapshots.put("ledger.activity", snapshotActivities(jdbc));
        snapshots.put("identity.user_account", snapshot(jdbc, "identity", "user_account"));
        snapshots.put("reference.instrument", snapshot(jdbc, "reference", "instrument"));
        return Map.copyOf(snapshots);
    }

    private static String snapshot(JdbcTemplate jdbc, String schema, String table) {
        return jdbc.queryForObject("SELECT COALESCE(string_agg(to_jsonb(snapshot_rows)::text, E'\\n' ORDER BY snapshot_rows.id), '')" +
                " FROM (SELECT * FROM " + schema + "." + table + ") snapshot_rows", String.class);
    }

    private static String snapshotActivities(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COALESCE(string_agg(jsonb_build_object(" +
                "'id', id, 'owner', owner_user_account_id, 'client_event', client_event_id, 'operation_scope', operation_scope," +
                " 'command_sequence', command_sequence, 'activity_type', activity_type, 'recording_mode', recording_mode, 'effective_at', effective_at," +
                " 'recorded_at', recorded_at, 'economic_sequence', economic_sequence, 'source_kind', source_kind, 'policy_decision', policy_decision," +
                " 'correction_reason', correction_reason, 'reverses_activity_id', reverses_activity_id, 'supersedes_activity_id', supersedes_activity_id)::text," +
                " E'\\n' ORDER BY id), '') FROM ledger.activity", String.class);
    }
}
