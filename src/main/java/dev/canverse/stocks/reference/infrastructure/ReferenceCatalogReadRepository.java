package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.application.InstrumentSearchCursor;
import dev.canverse.stocks.reference.domain.AliasType;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.MarketSessionStatus;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Explicit SQL read model for bounded reference and instrument response shapes. */
@Repository
@RequiredArgsConstructor
public class ReferenceCatalogReadRepository {

    private final JdbcClient jdbcClient;

    public List<CountryRow> findActiveCountries() {
        return jdbcClient
                .sql("SELECT code, name, active FROM reference.country WHERE active ORDER BY code")
                .query((rs, rowNum) ->
                        new CountryRow(rs.getString("code"), rs.getString("name"), rs.getBoolean("active")))
                .list();
    }

    public List<CurrencyRow> findActiveCurrencies() {
        return jdbcClient
                .sql("SELECT code, name, symbol, minor_unit, active FROM reference.currency WHERE active ORDER BY code")
                .query((rs, rowNum) -> new CurrencyRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("symbol"),
                        rs.getShort("minor_unit"),
                        rs.getBoolean("active")))
                .list();
    }

    public List<MarketRow> findActiveMarkets() {
        var rows = jdbcClient.sql("""
                        SELECT m.id, m.code, m.name, m.market_type, m.country_code, m.time_zone,
                               m.active, m.source_kind, mc.currency_code, mc.primary_quote
                        FROM reference.market m
                        LEFT JOIN reference.market_currency mc ON mc.market_id = m.id
                        WHERE m.active
                        ORDER BY m.code_normalized, mc.currency_code
                        """).query(this::mapMarketRow).list();

        var rowsByMarket =
                rows.stream().collect(Collectors.groupingBy(MarketRow::id, LinkedHashMap::new, Collectors.toList()));
        var quotationCurrenciesByMarket = rows.stream()
                .filter(row -> row.currencyCode() != null)
                .collect(Collectors.groupingBy(
                        MarketRow::id,
                        LinkedHashMap::new,
                        Collectors.mapping(MarketRow::currencyCode, Collectors.toList())));
        var primaryQuotationCurrencyByMarket = rows.stream()
                .filter(row -> row.primaryQuote() && row.currencyCode() != null)
                .collect(Collectors.toMap(
                        MarketRow::id, MarketRow::currencyCode, (first, ignored) -> first, LinkedHashMap::new));

        return rowsByMarket.values().stream()
                .map(marketRows -> {
                    var first = marketRows.getFirst();
                    return new MarketRow(
                            first.id(),
                            first.code(),
                            first.name(),
                            first.marketType(),
                            first.countryCode(),
                            first.timeZone(),
                            first.active(),
                            first.sourceKind(),
                            null,
                            false,
                            quotationCurrenciesByMarket.getOrDefault(first.id(), List.of()),
                            primaryQuotationCurrencyByMarket.get(first.id()));
                })
                .toList();
    }

    public Optional<MarketCalendarHeader> findActiveMarket(UUID marketId) {
        return jdbcClient
                .sql("""
                        SELECT id, code, time_zone
                        FROM reference.market
                        WHERE id = :marketId AND active
                        """)
                .param("marketId", Objects.requireNonNull(marketId, "marketId"))
                .query((rs, rowNum) -> new MarketCalendarHeader(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("time_zone")))
                .optional();
    }

    public List<CalendarRow> findCalendarRows(UUID marketId, LocalDate from, LocalDate to) {
        return jdbcClient
                .sql("""
                        SELECT calendar_date, session_status, opens_at, closes_at, source_kind
                        FROM reference.market_calendar
                        WHERE market_id = :marketId
                          AND calendar_date BETWEEN :fromDate AND :toDate
                        ORDER BY calendar_date
                        """)
                .param("marketId", Objects.requireNonNull(marketId, "marketId"))
                .param("fromDate", Objects.requireNonNull(from, "from"))
                .param("toDate", Objects.requireNonNull(to, "to"))
                .query((rs, rowNum) -> new CalendarRow(
                        rs.getObject("calendar_date", LocalDate.class),
                        MarketSessionStatus.valueOf(rs.getString("session_status")),
                        rs.getObject("opens_at", LocalTime.class),
                        rs.getObject("closes_at", LocalTime.class),
                        rs.getString("source_kind")))
                .list();
    }

    public Optional<InstrumentView> findVisibleInstrument(UUID ownerUserAccountId, UUID instrumentId) {
        var row = findInstrumentRow(
                """
                AND ((i.owner_user_account_id IS NULL AND i.active) OR i.owner_user_account_id = :ownerUserAccountId)
                AND i.id = :instrumentId
                """,
                statement -> statement
                        .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                        .param("instrumentId", Objects.requireNonNull(instrumentId, "instrumentId")));
        return row.map(instrumentRow -> new InstrumentView(
                instrumentRow, aliasesFor(List.of(instrumentRow.id())).getOrDefault(instrumentRow.id(), List.of())));
    }

    public List<InstrumentView> searchInstruments(
            UUID ownerUserAccountId,
            String queryNormalized,
            UUID marketId,
            InstrumentType type,
            boolean includeInactive,
            InstrumentSearchCursor cursor,
            int fetchLimit) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        var predicates = new ArrayList<String>();
        predicates.add("((i.owner_user_account_id IS NULL AND i.active)"
                + " OR (i.owner_user_account_id = :ownerUserAccountId"
                + " AND (:includeInactive OR i.active)))");
        if (queryNormalized != null) {
            predicates.add("(i.symbol_normalized LIKE :queryPrefix ESCAPE E'\\\\'"
                    + " OR i.name_normalized LIKE :queryPrefix ESCAPE E'\\\\'"
                    + " OR EXISTS (SELECT 1 FROM reference.instrument_alias qa"
                    + " WHERE qa.instrument_id = i.id AND qa.alias_normalized LIKE :queryPrefix ESCAPE E'\\\\'))");
        }
        if (marketId != null) {
            predicates.add("i.market_id = :marketId");
        }
        if (type != null) {
            predicates.add("i.instrument_type = :instrumentType");
        }
        if (cursor != null) {
            predicates.add("(i.symbol_normalized > :cursorSymbol"
                    + " OR (i.symbol_normalized = :cursorSymbol AND m.code_normalized > :cursorMarketCode)"
                    + " OR (i.symbol_normalized = :cursorSymbol AND m.code_normalized = :cursorMarketCode"
                    + " AND i.id > :cursorInstrumentId))");
        }

        var sql = """
                SELECT i.id, i.owner_user_account_id, i.market_id, m.code AS market_code,
                       m.code_normalized AS market_code_normalized,
                       i.symbol, i.symbol_normalized, i.name, i.name_normalized,
                       i.instrument_type, i.quotation_currency_code, i.valuation_method,
                       i.active, i.source_kind, i.version, i.created_at, i.updated_at
                FROM reference.instrument i
                JOIN reference.market m ON m.id = i.market_id
                WHERE %s
                ORDER BY i.symbol_normalized, m.code_normalized, i.id
                LIMIT :fetchLimit
                """.formatted(String.join(" AND ", predicates));
        var statement = jdbcClient
                .sql(sql)
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("includeInactive", includeInactive)
                .param("fetchLimit", fetchLimit);
        if (queryNormalized != null) {
            statement = statement.param("queryPrefix", escapeLikePrefix(queryNormalized) + "%");
        }
        if (marketId != null) {
            statement = statement.param("marketId", marketId);
        }
        if (type != null) {
            statement = statement.param("instrumentType", type.name());
        }
        if (cursor != null) {
            statement = statement
                    .param("cursorSymbol", cursor.symbolNormalized())
                    .param("cursorMarketCode", cursor.marketCodeNormalized())
                    .param("cursorInstrumentId", cursor.instrumentId());
        }
        var rows = statement.query(this::mapInstrumentRow).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        var aliases = aliasesFor(rows.stream().map(InstrumentRow::id).toList());
        return rows.stream()
                .map(row -> new InstrumentView(row, aliases.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private Optional<InstrumentRow> findInstrumentRow(
            String additionalPredicate, UnaryOperator<JdbcClient.StatementSpec> parameters) {
        var statement = jdbcClient.sql("""
                SELECT i.id, i.owner_user_account_id, i.market_id, m.code AS market_code,
                       m.code_normalized AS market_code_normalized,
                       i.symbol, i.symbol_normalized, i.name, i.name_normalized,
                       i.instrument_type, i.quotation_currency_code, i.valuation_method,
                       i.active, i.source_kind, i.version, i.created_at, i.updated_at
                FROM reference.instrument i
                JOIN reference.market m ON m.id = i.market_id
                WHERE 1 = 1
                """ + additionalPredicate);
        return parameters.apply(statement).query(this::mapInstrumentRow).optional();
    }

    private Map<UUID, List<AliasRow>> aliasesFor(List<UUID> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }
        var rows = jdbcClient
                .sql("""
                        SELECT id, instrument_id, alias_type, alias_value, alias_normalized
                        FROM reference.instrument_alias
                        WHERE instrument_id IN (:instrumentIds)
                        ORDER BY instrument_id, alias_type, alias_normalized, id
                        """)
                .param("instrumentIds", instrumentIds)
                .query(this::mapAliasRow)
                .list();
        var grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        AliasRow::instrumentId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
        return Collections.unmodifiableMap(grouped);
    }

    private static String escapeLikePrefix(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private MarketRow mapMarketRow(ResultSet rs, int rowNum) throws SQLException {
        return new MarketRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market_type"),
                rs.getString("country_code"),
                rs.getString("time_zone"),
                rs.getBoolean("active"),
                rs.getString("source_kind"),
                rs.getString("currency_code"),
                rs.getBoolean("primary_quote"));
    }

    private InstrumentRow mapInstrumentRow(ResultSet rs, int rowNum) throws SQLException {
        return new InstrumentRow(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_user_account_id", UUID.class),
                rs.getObject("market_id", UUID.class),
                rs.getString("market_code"),
                rs.getString("market_code_normalized"),
                rs.getString("symbol"),
                rs.getString("symbol_normalized"),
                rs.getString("name"),
                rs.getString("name_normalized"),
                InstrumentType.valueOf(rs.getString("instrument_type")),
                rs.getString("quotation_currency_code"),
                ValuationMethod.valueOf(rs.getString("valuation_method")),
                rs.getBoolean("active"),
                rs.getString("source_kind"),
                rs.getLong("version"),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private AliasRow mapAliasRow(ResultSet rs, int rowNum) throws SQLException {
        return new AliasRow(
                rs.getObject("id", UUID.class),
                rs.getObject("instrument_id", UUID.class),
                AliasType.valueOf(rs.getString("alias_type")),
                rs.getString("alias_value"),
                rs.getString("alias_normalized"));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record CountryRow(String code, String name, boolean active) {}

    public record CurrencyRow(String code, String name, String symbol, short minorUnit, boolean active) {}

    public record MarketRow(
            UUID id,
            String code,
            String name,
            String marketType,
            String countryCode,
            String timeZone,
            boolean active,
            String sourceKind,
            String currencyCode,
            boolean primaryQuote,
            List<String> quotationCurrencies,
            String primaryQuotationCurrency) {

        public MarketRow(
                UUID id,
                String code,
                String name,
                String marketType,
                String countryCode,
                String timeZone,
                boolean active,
                String sourceKind,
                String currencyCode,
                boolean primaryQuote,
                List<String> quotationCurrencies,
                String primaryQuotationCurrency) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.marketType = marketType;
            this.countryCode = countryCode;
            this.timeZone = timeZone;
            this.active = active;
            this.sourceKind = sourceKind;
            this.currencyCode = currencyCode;
            this.primaryQuote = primaryQuote;
            this.quotationCurrencies = quotationCurrencies == null ? null : List.copyOf(quotationCurrencies);
            this.primaryQuotationCurrency = primaryQuotationCurrency;
        }

        public MarketRow(
                UUID id,
                String code,
                String name,
                String marketType,
                String countryCode,
                String timeZone,
                boolean active,
                String sourceKind,
                String currencyCode,
                boolean primaryQuote) {
            this(
                    id,
                    code,
                    name,
                    marketType,
                    countryCode,
                    timeZone,
                    active,
                    sourceKind,
                    currencyCode,
                    primaryQuote,
                    null,
                    null);
        }
    }

    public record MarketCalendarHeader(UUID id, String code, String timeZone) {}

    public record CalendarRow(
            LocalDate date,
            MarketSessionStatus sessionStatus,
            LocalTime opensAt,
            LocalTime closesAt,
            String sourceKind) {}

    public record InstrumentRow(
            UUID id,
            UUID ownerId,
            UUID marketId,
            String marketCode,
            String marketCodeNormalized,
            String symbol,
            String symbolNormalized,
            String name,
            String nameNormalized,
            InstrumentType instrumentType,
            String quotationCurrency,
            ValuationMethod valuationMethod,
            boolean active,
            String sourceKind,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record AliasRow(UUID id, UUID instrumentId, AliasType type, String value, String normalizedValue) {}

    public record InstrumentView(InstrumentRow row, List<AliasRow> aliases) {

        public InstrumentView {
            aliases = List.copyOf(aliases);
        }
    }
}
