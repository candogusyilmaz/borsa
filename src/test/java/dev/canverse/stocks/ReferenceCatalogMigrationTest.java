package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class ReferenceCatalogMigrationTest {

    private static final UUID XIST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANUAL = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime CREATED = OffsetDateTime.of(2026, 8, 16, 9, 0, 0, 0, ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUserOwnedReferenceRows() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE reference.instrument_alias, reference.instrument, reference.market_calendar,"
                        + " identity.user_account CASCADE");
    }

    @Test
    @Order(1)
    void v2CreatesExactlyTheSevenReferenceTablesAndStableSeeds() {
        var tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'reference' ORDER BY table_name",
                String.class);
        assertThat(tables)
                .containsExactly(
                        "country",
                        "currency",
                        "instrument",
                        "instrument_alias",
                        "market",
                        "market_calendar",
                        "market_currency");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.country", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.currency", Integer.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.market", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.instrument", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reference.market_calendar", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.market_currency WHERE market_id = ? AND currency_code = ? AND primary_quote",
                        Integer.class,
                        XIST,
                        "TRY"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.market_currency WHERE market_id = ?", Integer.class, MANUAL))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code || ':' || name || ':' || active FROM reference.country ORDER BY code",
                        String.class))
                .containsExactly("GB:United Kingdom:true", "TR:Türkiye:true", "US:United States:true");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT code || ':' || name || ':' || symbol || ':' || minor_unit || ':' || active"
                                + " FROM reference.currency ORDER BY code",
                        String.class))
                .containsExactly(
                        "EUR:Euro:€:2:true",
                        "GBP:Pound sterling:£:2:true",
                        "TRY:Turkish lira:₺:2:true",
                        "USD:United States dollar:$:2:true");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id::text || ':' || code || ':' || name || ':' || market_type || ':'"
                                + " || coalesce(country_code, '<null>') || ':' || time_zone || ':' || active || ':' || source_kind"
                                + " FROM reference.market ORDER BY code",
                        String.class))
                .containsExactly(
                        "10000000-0000-0000-0000-000000000002:MANUAL:Manual or unlisted market:MANUAL:<null>:UTC:true:REFERENCE_SEED",
                        "10000000-0000-0000-0000-000000000001:XIST:Borsa Istanbul:EXCHANGE:TR:Europe/Istanbul:true:REFERENCE_SEED");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT market_id::text || '/' || currency_code || '/' || primary_quote"
                                + " FROM reference.market_currency ORDER BY market_id, currency_code",
                        String.class))
                .containsExactly(
                        "10000000-0000-0000-0000-000000000001/TRY/true",
                        "10000000-0000-0000-0000-000000000002/EUR/false",
                        "10000000-0000-0000-0000-000000000002/GBP/false",
                        "10000000-0000-0000-0000-000000000002/TRY/true",
                        "10000000-0000-0000-0000-000000000002/USD/false");
    }

    @Test
    @Order(2)
    void schemaColumnsConstraintsIndexesForeignKeysAndCommentsMatchTheContract() {
        assertColumns(
                "country",
                new ColumnDefinition("code", "text", "NO", null),
                new ColumnDefinition("name", "text", "NO", null),
                new ColumnDefinition("active", "boolean", "NO", "true"),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null));
        assertColumns(
                "currency",
                new ColumnDefinition("code", "text", "NO", null),
                new ColumnDefinition("name", "text", "NO", null),
                new ColumnDefinition("symbol", "text", "NO", null),
                new ColumnDefinition("minor_unit", "smallint", "NO", null),
                new ColumnDefinition("active", "boolean", "NO", "true"),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null));
        assertColumns(
                "market",
                new ColumnDefinition("id", "uuid", "NO", null),
                new ColumnDefinition("code", "text", "NO", null),
                new ColumnDefinition("code_normalized", "text", "NO", null),
                new ColumnDefinition("name", "text", "NO", null),
                new ColumnDefinition("market_type", "text", "NO", null),
                new ColumnDefinition("country_code", "text", "YES", null),
                new ColumnDefinition("time_zone", "text", "NO", null),
                new ColumnDefinition("active", "boolean", "NO", "true"),
                new ColumnDefinition("source_kind", "text", "NO", null),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null),
                new ColumnDefinition("updated_at", "timestamp with time zone", "NO", null));
        assertColumns(
                "market_currency",
                new ColumnDefinition("market_id", "uuid", "NO", null),
                new ColumnDefinition("currency_code", "text", "NO", null),
                new ColumnDefinition("primary_quote", "boolean", "NO", "false"));
        assertColumns(
                "instrument",
                new ColumnDefinition("id", "uuid", "NO", null),
                new ColumnDefinition("owner_user_account_id", "uuid", "YES", null),
                new ColumnDefinition("market_id", "uuid", "NO", null),
                new ColumnDefinition("symbol", "text", "NO", null),
                new ColumnDefinition("symbol_normalized", "text", "NO", null),
                new ColumnDefinition("name", "text", "NO", null),
                new ColumnDefinition("name_normalized", "text", "NO", null),
                new ColumnDefinition("instrument_type", "text", "NO", null),
                new ColumnDefinition("quotation_currency_code", "text", "NO", null),
                new ColumnDefinition("valuation_method", "text", "NO", null),
                new ColumnDefinition("active", "boolean", "NO", "true"),
                new ColumnDefinition("source_kind", "text", "NO", null),
                new ColumnDefinition("version", "bigint", "NO", "0"),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null),
                new ColumnDefinition("updated_at", "timestamp with time zone", "NO", null));
        assertColumns(
                "instrument_alias",
                new ColumnDefinition("id", "uuid", "NO", null),
                new ColumnDefinition("instrument_id", "uuid", "NO", null),
                new ColumnDefinition("alias_type", "text", "NO", null),
                new ColumnDefinition("alias_value", "text", "NO", null),
                new ColumnDefinition("alias_normalized", "text", "NO", null),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null));
        assertColumns(
                "market_calendar",
                new ColumnDefinition("market_id", "uuid", "NO", null),
                new ColumnDefinition("calendar_date", "date", "NO", null),
                new ColumnDefinition("session_status", "text", "NO", null),
                new ColumnDefinition("opens_at", "time without time zone", "YES", null),
                new ColumnDefinition("closes_at", "time without time zone", "YES", null),
                new ColumnDefinition("source_kind", "text", "NO", null),
                new ColumnDefinition("created_at", "timestamp with time zone", "NO", null));

        assertThat(constraintNames("country"))
                .containsExactlyInAnyOrder(
                        "ck_reference_country_code", "ck_reference_country_name", "pk_reference_country");
        assertThat(constraintNames("currency"))
                .containsExactlyInAnyOrder(
                        "ck_reference_currency_code",
                        "ck_reference_currency_minor_unit",
                        "ck_reference_currency_name",
                        "ck_reference_currency_symbol",
                        "pk_reference_currency");
        assertThat(constraintNames("market"))
                .containsExactlyInAnyOrder(
                        "ck_reference_market_code",
                        "ck_reference_market_code_normalized",
                        "ck_reference_market_name",
                        "ck_reference_market_source_kind",
                        "ck_reference_market_time_zone",
                        "ck_reference_market_type",
                        "fk_reference_market_country",
                        "pk_reference_market",
                        "uq_reference_market_code_normalized");
        assertThat(constraintNames("market_currency"))
                .containsExactlyInAnyOrder(
                        "fk_reference_market_currency_currency",
                        "fk_reference_market_currency_market",
                        "pk_reference_market_currency");
        assertThat(constraintNames("instrument"))
                .containsExactlyInAnyOrder(
                        "ck_reference_instrument_name",
                        "ck_reference_instrument_name_normalized",
                        "ck_reference_instrument_owner_source",
                        "ck_reference_instrument_source_kind",
                        "ck_reference_instrument_symbol",
                        "ck_reference_instrument_symbol_normalized",
                        "ck_reference_instrument_type",
                        "ck_reference_instrument_valuation_method",
                        "ck_reference_instrument_version_non_negative",
                        "fk_reference_instrument_market",
                        "fk_reference_instrument_market_currency",
                        "fk_reference_instrument_owner",
                        "pk_reference_instrument");
        assertThat(constraintNames("instrument_alias"))
                .containsExactlyInAnyOrder(
                        "ck_reference_instrument_alias_normalized",
                        "ck_reference_instrument_alias_type",
                        "ck_reference_instrument_alias_value",
                        "fk_reference_instrument_alias_instrument",
                        "pk_reference_instrument_alias",
                        "uix_reference_instrument_alias_identity");
        assertThat(constraintNames("market_calendar"))
                .containsExactlyInAnyOrder(
                        "ck_reference_market_calendar_source_kind",
                        "ck_reference_market_calendar_status",
                        "ck_reference_market_calendar_time_shape",
                        "fk_reference_market_calendar_market",
                        "pk_reference_market_calendar");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = 'reference' ORDER BY indexname",
                        String.class))
                .containsExactly(
                        "ix_reference_instrument_alias_exact",
                        "ix_reference_instrument_alias_prefix",
                        "ix_reference_instrument_global_visibility",
                        "ix_reference_instrument_market_type",
                        "ix_reference_instrument_name_prefix",
                        "ix_reference_instrument_owner_visibility",
                        "pk_reference_country",
                        "pk_reference_currency",
                        "pk_reference_instrument",
                        "pk_reference_instrument_alias",
                        "pk_reference_market",
                        "pk_reference_market_calendar",
                        "pk_reference_market_currency",
                        "uix_reference_instrument_alias_identity",
                        "uix_reference_instrument_global_symbol",
                        "uix_reference_instrument_owner_symbol",
                        "uix_reference_market_currency_primary",
                        "uq_reference_market_code_normalized");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = 'reference'"
                                + " AND indexname IN ('uix_reference_instrument_global_symbol',"
                                + " 'uix_reference_instrument_owner_symbol', 'ix_reference_instrument_global_visibility',"
                                + " 'ix_reference_instrument_owner_visibility', 'ix_reference_instrument_market_type')"
                                + " AND indexdef LIKE '%text_pattern_ops%' ORDER BY indexname",
                        String.class))
                .containsExactly(
                        "ix_reference_instrument_global_visibility",
                        "ix_reference_instrument_market_type",
                        "ix_reference_instrument_owner_visibility",
                        "uix_reference_instrument_global_symbol",
                        "uix_reference_instrument_owner_symbol");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT constraint_name || ':' || delete_rule"
                                + " FROM information_schema.referential_constraints"
                                + " WHERE constraint_schema = 'reference' ORDER BY constraint_name",
                        String.class))
                .containsExactly(
                        "fk_reference_instrument_alias_instrument:CASCADE",
                        "fk_reference_instrument_market:RESTRICT",
                        "fk_reference_instrument_market_currency:RESTRICT",
                        "fk_reference_instrument_owner:CASCADE",
                        "fk_reference_market_calendar_market:CASCADE",
                        "fk_reference_market_country:RESTRICT",
                        "fk_reference_market_currency_currency:RESTRICT",
                        "fk_reference_market_currency_market:CASCADE");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT table_name || '=' || obj_description(('reference.' || table_name)::regclass, 'pg_class')"
                                + " FROM information_schema.tables WHERE table_schema = 'reference'"
                                + " ORDER BY table_name",
                        String.class))
                .containsExactly(
                        "country=Stable ISO-like country identities used by the reference catalogue.",
                        "currency=Stable currency identities and display metadata; no exchange rates.",
                        "instrument=Global or owner-entered instrument identities without observations or positions.",
                        "instrument_alias=Search aliases for canonical instrument identities.",
                        "market=Market identities and timezone metadata; no trading-hours inference.",
                        "market_calendar=Explicit known local market-date coverage; missing rows remain unknown.",
                        "market_currency=Explicit market quotation-currency support relationships.");
    }

    @Test
    @Order(3)
    void forbiddenDatabaseObjectsAndNumericReferenceIdsAreAbsent() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_type WHERE typnamespace = 'reference'::regnamespace AND typtype = 'e'",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_extension WHERE extname <> 'plpgsql'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_proc WHERE pronamespace = 'reference'::regnamespace", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_trigger t"
                                + " JOIN pg_class c ON c.oid = t.tgrelid"
                                + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + " WHERE n.nspname = 'reference' AND NOT t.tgisinternal",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns"
                                + " WHERE table_schema = 'reference'"
                                + " AND (column_name = 'id' OR column_name LIKE '%\\_id')"
                                + " AND data_type <> 'uuid'",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns"
                                + " WHERE table_schema = 'reference'"
                                + " AND column_name ~ '(price|rate|snapshot|quantity|balance|provider)'",
                        Integer.class))
                .isZero();
    }

    @Test
    @Order(4)
    void sourceOwnerAndReferenceIntegrityRulesAreDatabaseEnforced() {
        var userId = insertUser("migration-owner@example.com");

        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), userId, MANUAL, "OWNER-MISMATCH", "REFERENCE_SEED", "TRY"));
        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), null, MANUAL, "GLOBAL-MISMATCH", "USER_ENTERED", "TRY"));
        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), userId, XIST, "UNSUPPORTED", "USER_ENTERED", "EUR"));
    }

    @Test
    @Order(5)
    void duplicateSymbolsAliasesCalendarShapesAndPrimaryQuotesAreRejected() {
        var userId = insertUser("duplicate-owner@example.com");
        var instrumentId = UUID.randomUUID();
        insertInstrument(instrumentId, userId, MANUAL, "DUPLICATE", "USER_ENTERED", "TRY");

        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), userId, MANUAL, "duplicate", "USER_ENTERED", "TRY"));
        insertInstrument(UUID.randomUUID(), null, MANUAL, "GLOBAL-DUPLICATE", "REFERENCE_SEED", "TRY");
        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), null, MANUAL, "global-duplicate", "REFERENCE_SEED", "TRY"));
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                instrumentId,
                "USER",
                "My alias",
                "MY ALIAS",
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                instrumentId,
                "USER",
                "My alias again",
                "MY ALIAS",
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_currency (market_id, currency_code, primary_quote) VALUES (?, ?, true)",
                MANUAL,
                "USD"));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'OPEN', NULL, NULL, 'USER_ENTERED', ?)",
                MANUAL,
                LocalDate.of(2026, 8, 16),
                CREATED));
    }

    @Test
    @Order(6)
    void deletingUsersCascadesOnlyTheirManualInstrumentRowsAndStableReferencesRemainRestricted() {
        var userId = insertUser("cascade-reference@example.com");
        var instrumentId = UUID.randomUUID();
        insertInstrument(instrumentId, userId, MANUAL, "CASCADE", "USER_ENTERED", "TRY");
        jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, 'USER', 'cascade alias', 'CASCADE ALIAS', ?)",
                UUID.randomUUID(),
                instrumentId,
                CREATED);

        inTransaction(() -> jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE id = ?", Integer.class, instrumentId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument_alias WHERE instrument_id = ?",
                        Integer.class,
                        instrumentId))
                .isZero();
        assertConstraintViolation(() -> jdbcTemplate.update("DELETE FROM reference.currency WHERE code = 'TRY'"));
        assertConstraintViolation(() -> jdbcTemplate.update("DELETE FROM reference.country WHERE code = 'TR'"));
        assertConstraintViolation(() -> jdbcTemplate.update("DELETE FROM reference.market WHERE id = ?", MANUAL));
    }

    @Test
    @Order(7)
    void secondOwnerCascadeIsolationAndMarketCascadeDeleteActionsAreProven() {
        var firstOwner = insertUser("cascade-first@example.com");
        var secondOwner = insertUser("cascade-second@example.com");
        var firstInstrument = UUID.randomUUID();
        var secondInstrument = UUID.randomUUID();
        insertInstrument(firstInstrument, firstOwner, MANUAL, "SHARED", "USER_ENTERED", "TRY");
        insertInstrument(secondInstrument, secondOwner, MANUAL, "SHARED", "USER_ENTERED", "TRY");
        insertAlias(firstInstrument, "first owner alias");
        insertAlias(secondInstrument, "second owner alias");

        inTransaction(() -> jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", firstOwner));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE id = ?", Integer.class, firstInstrument))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument_alias WHERE instrument_id = ?",
                        Integer.class,
                        firstInstrument))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE id = ?", Integer.class, secondInstrument))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument_alias WHERE instrument_id = ?",
                        Integer.class,
                        secondInstrument))
                .isEqualTo(1);

        var temporaryMarket = UUID.randomUUID();
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.market"
                        + " (id, code, code_normalized, name, market_type, country_code, time_zone, source_kind, created_at, updated_at)"
                        + " VALUES (?, 'TEMP', 'TEMP', 'Temporary market', 'MANUAL', NULL, 'UTC', 'REFERENCE_SEED', ?, ?)",
                temporaryMarket,
                CREATED,
                CREATED));
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_currency (market_id, currency_code, primary_quote) VALUES (?, 'TRY', true)",
                temporaryMarket));
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'CLOSED', NULL, NULL, 'USER_ENTERED', ?)",
                temporaryMarket,
                LocalDate.of(2026, 8, 16),
                CREATED));
        inTransaction(() -> jdbcTemplate.update("DELETE FROM reference.market WHERE id = ?", temporaryMarket));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.market_currency WHERE market_id = ?",
                        Integer.class,
                        temporaryMarket))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.market_calendar WHERE market_id = ?",
                        Integer.class,
                        temporaryMarket))
                .isZero();
    }

    @Test
    @Order(8)
    void unknownForeignKeysAndFormatBlankAndCalendarShapeChecksAreRejected() {
        var userId = insertUser("integrity-shapes@example.com");
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.country (code, name, created_at) VALUES ('t', 'Name', ?)", CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.country (code, name, created_at) VALUES ('ZZ', ' ', ?)", CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.currency (code, name, symbol, minor_unit, created_at)"
                        + " VALUES ('eu', 'Euro', '€', 2, ?)",
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.currency (code, name, symbol, minor_unit, created_at)"
                        + " VALUES ('ZZZ', 'Euro', ' ', 2, ?)",
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.currency (code, name, symbol, minor_unit, created_at)"
                        + " VALUES ('ZZZ', 'Euro', '$', 19, ?)",
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market"
                        + " (id, code, code_normalized, name, market_type, country_code, time_zone, source_kind, created_at, updated_at)"
                        + " VALUES (?, 'BAD CODE', 'BAD CODE', 'Name', 'MANUAL', NULL, 'UTC', 'REFERENCE_SEED', ?, ?)",
                UUID.randomUUID(),
                CREATED,
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market"
                        + " (id, code, code_normalized, name, market_type, country_code, time_zone, source_kind, created_at, updated_at)"
                        + " VALUES (?, 'TEMP2', 'temp2', 'Name', 'MANUAL', 'ZZ', 'UTC', 'REFERENCE_SEED', ?, ?)",
                UUID.randomUUID(),
                CREATED,
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_currency (market_id, currency_code) VALUES (?, 'ZZZ')", MANUAL));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_currency (market_id, currency_code) VALUES (?, 'TRY')",
                UUID.randomUUID()));
        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), userId, MANUAL, "BAD SYMBOL", "USER_ENTERED", "TRY"));
        assertConstraintViolation(() ->
                insertInstrument(UUID.randomUUID(), UUID.randomUUID(), MANUAL, "UNKNOWN-OWNER", "USER_ENTERED", "TRY"));
        assertConstraintViolation(() -> insertInstrument(
                UUID.randomUUID(), userId, UUID.randomUUID(), "UNKNOWN-MARKET", "USER_ENTERED", "TRY"));
        assertConstraintViolation(
                () -> insertInstrument(UUID.randomUUID(), userId, XIST, "UNKNOWN-CURRENCY", "USER_ENTERED", "USD"));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, 'USER', 'Alias', ' ', ?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'UNKNOWN', NULL, NULL, 'USER_ENTERED', ?)",
                MANUAL,
                LocalDate.of(2026, 8, 20),
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'OPEN', '09:00', NULL, 'USER_ENTERED', ?)",
                MANUAL,
                LocalDate.of(2026, 8, 21),
                CREATED));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'CLOSED', '09:00', '10:00', 'USER_ENTERED', ?)",
                MANUAL,
                LocalDate.of(2026, 8, 22),
                CREATED));
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                email,
                CREATED,
                CREATED));
        return id;
    }

    private void insertInstrument(
            UUID id, UUID ownerId, UUID marketId, String symbol, String sourceKind, String quotationCurrency) {
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument"
                        + " (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized,"
                        + " instrument_type, quotation_currency_code, valuation_method, source_kind, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'Migration instrument', 'MIGRATION INSTRUMENT', 'FUND', ?,"
                        + " 'MANUAL_VALUE', ?, ?, ?)",
                id,
                ownerId,
                marketId,
                symbol,
                symbol.toUpperCase(Locale.ROOT),
                quotationCurrency,
                sourceKind,
                CREATED,
                CREATED));
    }

    private void insertAlias(UUID instrumentId, String value) {
        inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, 'USER', ?, ?, ?)",
                UUID.randomUUID(),
                instrumentId,
                value,
                value.toUpperCase(Locale.ROOT),
                CREATED));
    }

    private List<String> constraintNames(String table) {
        return jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = to_regclass(?) ORDER BY conname",
                String.class,
                "reference." + table);
    }

    private void assertColumns(String table, ColumnDefinition... expected) {
        var actual = jdbcTemplate.query(
                "SELECT column_name, data_type, is_nullable, column_default"
                        + " FROM information_schema.columns"
                        + " WHERE table_schema = 'reference' AND table_name = ?"
                        + " ORDER BY ordinal_position",
                (resultSet, rowNum) -> new ColumnDefinition(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getString("is_nullable"),
                        resultSet.getString("column_default")),
                table);
        assertThat(actual).containsExactly(expected);
    }

    private void inTransaction(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        new TransactionTemplate(transactionManager).execute(status -> {
            action.run();
            return null;
        });
    }

    private void assertConstraintViolation(Runnable action) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> template.execute(status -> {
                    action.run();
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private record ColumnDefinition(String name, String dataType, String nullable, String defaultValue) {}
}
