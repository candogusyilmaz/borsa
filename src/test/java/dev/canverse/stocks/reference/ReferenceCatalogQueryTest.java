package dev.canverse.stocks.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.web.SliceResponse;
import dev.canverse.stocks.reference.application.InstrumentSearchService;
import dev.canverse.stocks.reference.application.ReferenceCatalogQueryService;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCriteria;
import dev.canverse.stocks.reference.domain.CalendarCoverageStatus;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.MarketSessionStatus;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import dev.canverse.stocks.reference.web.response.InstrumentSummaryResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
@Import(ReferenceCatalogQueryTest.TestOverrides.class)
class ReferenceCatalogQueryTest {

    private static final UUID XIST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANUAL = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID USER_ONE = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID USER_TWO = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID GLOBAL_MANUAL = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID GLOBAL_XIST = UUID.fromString("70000000-0000-4000-8000-000000000002");
    private static final UUID OWNER_SAME = UUID.fromString("70000000-0000-4000-8000-000000000003");
    private static final UUID OWNER_INACTIVE = UUID.fromString("70000000-0000-4000-8000-000000000004");
    private static final UUID GLOBAL_INACTIVE = UUID.fromString("70000000-0000-4000-8000-000000000005");
    private static final UUID PERCENT = UUID.fromString("70000000-0000-4000-8000-000000000006");
    private static final UUID UNDERSCORE = UUID.fromString("70000000-0000-4000-8000-000000000007");
    private static final UUID BACKSLASH = UUID.fromString("70000000-0000-4000-8000-000000000008");
    private static final UUID OTHER_OWNER = UUID.fromString("70000000-0000-4000-8000-000000000009");
    private static final OffsetDateTime CREATED = OffsetDateTime.of(2026, 8, 16, 9, 0, 0, 0, ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ReferenceCatalogQueryService queryService;

    @Autowired
    InstrumentSearchService searchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCalendar() {
        jdbcTemplate.execute("DELETE FROM reference.market_calendar");
    }

    @Test
    void seededReadsAreStableOfflineFactsWithMarketCurrencyOrdering() {
        var countries = queryService.countries();
        var currencies = queryService.currencies();
        var markets = queryService.markets();

        assertThat(countries).extracting(response -> response.code()).containsExactly("GB", "TR", "US");
        assertThat(currencies).extracting(response -> response.code()).containsExactly("EUR", "GBP", "TRY", "USD");
        assertThat(markets).extracting(response -> response.code()).containsExactly("MANUAL", "XIST");
        assertThat(markets.get(1).quotationCurrencies()).containsExactly("TRY");
        assertThat(markets.get(1).primaryQuotationCurrency()).isEqualTo("TRY");
        assertThat(markets.getFirst().quotationCurrencies()).containsExactly("EUR", "GBP", "TRY", "USD");
        assertThat(markets.getFirst().primaryQuotationCurrency()).isEqualTo("TRY");
        assertThat(markets).allSatisfy(market -> assertThat(market.sourceKind()).isEqualTo("REFERENCE_SEED"));
    }

    @Test
    void emptyCalendarReturnsNoneAndEveryDateAsMissingWithoutInference() {
        var response = queryService.calendar(XIST, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

        assertThat(response.coverageStatus()).isEqualTo(CalendarCoverageStatus.NONE);
        assertThat(response.sessions()).isEmpty();
        assertThat(response.missingDates())
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3));
        assertThat(response.timeZone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void partialAndCompleteCalendarsReturnExplicitRowsAndLocalTimes() {
        insertCalendar(LocalDate.of(2026, 8, 1), "OPEN", LocalTime.of(9, 30), LocalTime.of(17, 0));
        insertCalendar(LocalDate.of(2026, 8, 3), "CLOSED", null, null);

        var partial = queryService.calendar(XIST, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        assertThat(partial.coverageStatus()).isEqualTo(CalendarCoverageStatus.PARTIAL);
        assertThat(partial.sessions())
                .extracting(session -> session.date())
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        assertThat(partial.sessions().getFirst().sessionStatus()).isEqualTo(MarketSessionStatus.OPEN);
        assertThat(partial.sessions().getFirst().opensAt()).isEqualTo(LocalTime.of(9, 30));
        assertThat(partial.sessions().getLast().opensAt()).isNull();
        assertThat(partial.missingDates()).containsExactly(LocalDate.of(2026, 8, 2));

        insertCalendar(LocalDate.of(2026, 8, 2), "CLOSED", null, null);
        var complete = queryService.calendar(XIST, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));
        assertThat(complete.coverageStatus()).isEqualTo(CalendarCoverageStatus.COMPLETE);
        assertThat(complete.missingDates()).isEmpty();
    }

    @Test
    void invalidCalendarRangeAndUnknownMarketFailBeforeReadingRows() {
        executedStatements.set(0);
        assertThatThrownBy(() -> queryService.calendar(XIST, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThat(executedStatements).hasValue(0);
        assertThatThrownBy(() -> queryService.calendar(XIST, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() ->
                        queryService.calendar(UUID.randomUUID(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ReferenceErrorCode.MARKET_NOT_FOUND));
    }

    @Test
    void calendarAcceptsDateEqualityAndTheExactMaximumOf366Dates() {
        var date = LocalDate.of(2026, 8, 16);
        var singleDate = queryService.calendar(XIST, date, date);

        assertThat(singleDate.coverageStatus()).isEqualTo(CalendarCoverageStatus.NONE);
        assertThat(singleDate.missingDates()).containsExactly(date);

        var first = LocalDate.of(2024, 1, 1);
        var last = LocalDate.of(2024, 12, 31);
        var maximum = queryService.calendar(XIST, first, last);

        assertThat(maximum.coverageStatus()).isEqualTo(CalendarCoverageStatus.NONE);
        assertThat(maximum.missingDates()).hasSize(366);
        assertThat(maximum.missingDates()).startsWith(first).endsWith(last);
    }

    @Test
    void invalidPersistedMarketTimezoneFailsExplicitly() {
        jdbcTemplate.update("UPDATE reference.market SET time_zone = 'not/a-zone' WHERE id = ?", XIST);

        assertThatThrownBy(queryService::markets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reference market has an invalid IANA timezone");
    }

    @Test
    void instrumentSearchCoversPrefixesFiltersAliasesWildcardsAndVisibilityInSql() {
        insertUser(USER_ONE, "query-owner-one@example.com");
        insertUser(USER_TWO, "query-owner-two@example.com");
        insertInstrument(GLOBAL_MANUAL, null, MANUAL, "SAME", "Global shared fund", "FUND", true, "SHARED_ALIAS");
        insertInstrument(GLOBAL_XIST, null, XIST, "SAME", "Global exchange fund", "ETF", true, "SHARED_ALIAS");
        insertInstrument(OWNER_SAME, USER_ONE, MANUAL, "SAME", "Owner shared fund", "FUND", true, "shared_alias");
        insertInstrument(
                OWNER_INACTIVE, USER_ONE, MANUAL, "HIDDEN-OWNER", "Hidden owner fund", "FUND", false, "HIDDEN_OWNER");
        insertInstrument(
                GLOBAL_INACTIVE, null, MANUAL, "HIDDEN-GLOBAL", "Hidden global fund", "FUND", false, "HIDDEN_GLOBAL");
        insertInstrument(PERCENT, USER_ONE, MANUAL, "PERCENT", "Literal%Name", "FUND", true, "Literal%Alias");
        insertInstrument(UNDERSCORE, USER_ONE, MANUAL, "UNDERSCORE", "Literal_Name", "ETF", true, "Literal_Alias");
        insertInstrument(BACKSLASH, USER_ONE, MANUAL, "BACKSLASH", "Literal\\Name", "FUND", true, "Literal\\Alias");
        insertInstrument(
                OTHER_OWNER, USER_TWO, MANUAL, "OTHER-OWNER", "Other owner fund", "FUND", true, "SHARED_ALIAS");

        var same = search(USER_ONE, "same", null, null, false, 20);
        assertThat(same.items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(GLOBAL_XIST, GLOBAL_MANUAL, OWNER_SAME);
        assertThat(same.items())
                .extracting(InstrumentSummaryResponse::marketCode)
                .containsExactly("XIST", "MANUAL", "MANUAL");
        assertThat(same.items())
                .allSatisfy(summary -> assertThat(summary.aliases()).hasSize(1));

        assertThat(search(USER_ONE, "shared_alias", null, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(GLOBAL_XIST, GLOBAL_MANUAL, OWNER_SAME);
        assertThat(search(USER_ONE, "literal%", null, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(PERCENT);
        assertThat(search(USER_ONE, "literal_", null, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(UNDERSCORE);
        assertThat(search(USER_ONE, "literal\\", null, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(BACKSLASH);
        assertThat(search(USER_ONE, null, XIST, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(GLOBAL_XIST);
        assertThat(search(USER_ONE, null, MANUAL, InstrumentType.ETF, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(UNDERSCORE);
        assertThat(search(USER_ONE, "hidden", null, null, false, 20).items()).isEmpty();
        assertThat(search(USER_ONE, "hidden", null, null, true, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(OWNER_INACTIVE);
        assertThat(search(USER_ONE, null, null, null, false, 20).items())
                .extracting(InstrumentSummaryResponse::id)
                .doesNotContain(OTHER_OWNER, OWNER_INACTIVE, GLOBAL_INACTIVE);
    }

    @Test
    void instrumentSearchPagesHaveCorrectOffsetLookAheadAndOwnerVisibility() {
        insertUser(USER_ONE, "page-owner-one@example.com");
        insertUser(USER_TWO, "page-owner-two@example.com");
        var expected = new ArrayList<UUID>();
        for (var index = 0; index < 41; index++) {
            var id = UUID.fromString("71000000-0000-4000-8000-%012d".formatted(index + 1));
            expected.add(id);
            insertInstrument(
                    id,
                    USER_ONE,
                    MANUAL,
                    "PAGE-%03d".formatted(index),
                    "Page fund " + index,
                    "FUND",
                    true,
                    "PAGE-ALIAS-%03d".formatted(index));
        }
        var otherOwnerId = UUID.fromString("71000000-0000-4000-8000-000000000100");
        insertInstrument(otherOwnerId, USER_TWO, MANUAL, "PAGE-999", "Other page fund", "FUND", true, "OTHER_PAGE");

        var firstPage = search(USER_ONE, "page-", null, null, false, PageRequest.of(0, 7, symbolSort()));
        var secondPage = search(USER_ONE, "page-", null, null, false, PageRequest.of(1, 7, symbolSort()));
        var pastEndPage = search(USER_ONE, "page-", null, null, false, PageRequest.of(6, 7, symbolSort()));

        assertThat(firstPage.items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactlyElementsOf(expected.subList(0, 7));
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(7);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.items()).doesNotHaveDuplicates();
        assertThat(secondPage.items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactlyElementsOf(expected.subList(7, 14));
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(pastEndPage.items()).isEmpty();
        assertThat(pastEndPage.page()).isEqualTo(6);
        assertThat(pastEndPage.size()).isEqualTo(7);
        assertThat(pastEndPage.hasNext()).isFalse();

        var otherOwnerPage = search(USER_TWO, "page-", null, null, false, PageRequest.of(0, 100, symbolSort()));
        assertThat(otherOwnerPage.items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(otherOwnerId);

        executedStatements.set(0);
        lastAliasQueryIds.set(List.of());
        var countedPage = search(USER_ONE, "page-", null, null, false, PageRequest.of(0, 20, symbolSort()));
        assertThat(countedPage.items()).hasSize(20);
        assertThat(countedPage.hasNext()).isTrue();
        assertThat(executedStatements).hasValue(2);
        assertThat(lastAliasQueryIds.get()).containsExactlyElementsOf(expected.subList(0, 20));
        assertThat(lastAliasQueryIds.get()).doesNotContain(expected.get(20));
    }

    @Test
    void instrumentSearchSortsUseNormalizedValuesAndCompleteTieBreakers() {
        insertUser(USER_ONE, "sort-owner@example.com");
        var nameLow = UUID.fromString("73000000-0000-4000-8000-000000000011");
        var nameTieFirst = UUID.fromString("73000000-0000-4000-8000-000000000012");
        var nameTieSecond = UUID.fromString("73000000-0000-4000-8000-000000000013");
        var nameHigh = UUID.fromString("73000000-0000-4000-8000-000000000014");
        insertInstrument(nameLow, USER_ONE, MANUAL, "NAME-LOW", "Aardvark", "FUND", true, "name-low");
        insertInstrument(nameTieFirst, USER_ONE, MANUAL, "NAME-TIE-A", "Same Name", "FUND", true, "name-tie-a");
        insertInstrument(nameTieSecond, USER_ONE, MANUAL, "NAME-TIE-B", "Same Name", "FUND", true, "name-tie-b");
        insertInstrument(nameHigh, USER_ONE, MANUAL, "NAME-HIGH", "Zulu", "FUND", true, "name-high");

        assertThat(search(USER_ONE, "NAME-", null, null, false, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name"))))
                        .items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(nameLow, nameTieFirst, nameTieSecond, nameHigh);
        assertThat(search(USER_ONE, "NAME-", null, null, false, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("name"))))
                        .items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(nameHigh, nameTieSecond, nameTieFirst, nameLow);

        var symbolManualGlobal = UUID.fromString("73000000-0000-4000-8000-000000000021");
        var symbolManualOwner = UUID.fromString("73000000-0000-4000-8000-000000000022");
        var symbolXistGlobal = UUID.fromString("73000000-0000-4000-8000-000000000023");
        insertInstrument(
                symbolManualGlobal, null, MANUAL, "SAME-SYMBOL", "Symbol global manual", "FUND", true, "symbol-global");
        insertInstrument(
                symbolManualOwner,
                USER_ONE,
                MANUAL,
                "SAME-SYMBOL",
                "Symbol owner manual",
                "FUND",
                true,
                "symbol-owner");
        insertInstrument(
                symbolXistGlobal, null, XIST, "SAME-SYMBOL", "Symbol global xist", "FUND", true, "symbol-xist");

        assertThat(search(
                                USER_ONE,
                                "SAME-SYMBOL",
                                null,
                                null,
                                false,
                                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("symbol"))))
                        .items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(symbolManualGlobal, symbolManualOwner, symbolXistGlobal);
        assertThat(search(
                                USER_ONE,
                                "SAME-SYMBOL",
                                null,
                                null,
                                false,
                                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("symbol"))))
                        .items())
                .extracting(InstrumentSummaryResponse::id)
                .containsExactly(symbolXistGlobal, symbolManualOwner, symbolManualGlobal);
    }

    @Test
    void instrumentSearchRejectsUnsupportedSortShapesBeforeBuildingSql() {
        var criteria = new InstrumentSearchCriteria(null, null, null, false);

        assertThatThrownBy(() -> searchService.search(
                        USER_ONE, criteria, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("marketId")))))
                .isInstanceOf(AppException.class)
                .satisfies(exception ->
                        assertThat(((AppException) exception).getCode()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> searchService.search(
                        USER_ONE,
                        criteria,
                        PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name"), Sort.Order.desc("symbol")))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> searchService.search(
                        USER_ONE,
                        criteria,
                        PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name").ignoreCase()))))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> searchService.search(
                        USER_ONE,
                        criteria,
                        PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name").nullsFirst()))))
                .isInstanceOf(AppException.class);
    }

    @Test
    void emptyInstrumentSliceDoesNotLoadAliasesOrRunCountQuery() {
        executedStatements.set(0);

        var slice = search(USER_ONE, "NO-SUCH-INSTRUMENT", null, null, false, PageRequest.of(0, 7, symbolSort()));

        assertThat(slice.items()).isEmpty();
        assertThat(slice.hasNext()).isFalse();
        assertThat(executedStatements).hasValue(1);
    }

    private SliceResponse<InstrumentSummaryResponse> search(
            UUID ownerUserAccountId,
            String query,
            UUID marketId,
            InstrumentType type,
            boolean includeInactive,
            int size) {
        return search(
                ownerUserAccountId,
                query,
                marketId,
                type,
                includeInactive,
                PageRequest.of(0, size, Sort.by(Sort.Order.asc("name"))));
    }

    private SliceResponse<InstrumentSummaryResponse> search(
            UUID ownerUserAccountId,
            String query,
            UUID marketId,
            InstrumentType type,
            boolean includeInactive,
            PageRequest pageable) {
        return searchService.search(
                ownerUserAccountId, new InstrumentSearchCriteria(query, marketId, type, includeInactive), pageable);
    }

    private static Sort symbolSort() {
        return Sort.by(Sort.Order.asc("symbol"));
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                email,
                CREATED,
                CREATED);
    }

    private void insertInstrument(
            UUID id,
            UUID ownerId,
            UUID marketId,
            String symbol,
            String name,
            String instrumentType,
            boolean active,
            String alias) {
        var normalizedSymbol = symbol.toUpperCase(java.util.Locale.ROOT);
        var normalizedName = name.toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update(
                "INSERT INTO reference.instrument"
                        + " (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized,"
                        + " instrument_type, quotation_currency_code, valuation_method, active, source_kind, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'TRY', 'MANUAL_VALUE', ?, ?, 0, ?, ?)",
                id,
                ownerId,
                marketId,
                symbol,
                normalizedSymbol,
                name,
                normalizedName,
                instrumentType,
                active,
                ownerId == null ? "REFERENCE_SEED" : "USER_ENTERED",
                CREATED,
                CREATED);
        jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                        + " VALUES (?, ?, 'USER', ?, ?, ?)",
                UUID.randomUUID(),
                id,
                alias,
                alias.toUpperCase(java.util.Locale.ROOT),
                CREATED);
    }

    private void insertCalendar(LocalDate date, String status, LocalTime opensAt, LocalTime closesAt) {
        jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'USER_ENTERED', ?)",
                XIST,
                date,
                status,
                opensAt,
                closesAt,
                CREATED);
    }

    private static final AtomicLong executedStatements = new AtomicLong();
    private static final AtomicReference<List<UUID>> lastAliasQueryIds = new AtomicReference<>(List.of());

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        static BeanPostProcessor dataSourceQueryCountingPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof javax.sql.DataSource dataSource) {
                        return java.lang.reflect.Proxy.newProxyInstance(
                                javax.sql.DataSource.class.getClassLoader(),
                                new Class<?>[] {javax.sql.DataSource.class},
                                (proxy, method, args) -> {
                                    if ("getConnection".equals(method.getName())) {
                                        var connection = (java.sql.Connection) method.invoke(dataSource, args);
                                        return java.lang.reflect.Proxy.newProxyInstance(
                                                java.sql.Connection.class.getClassLoader(),
                                                new Class<?>[] {java.sql.Connection.class},
                                                (connProxy, connMethod, connArgs) -> {
                                                    if ("prepareStatement".equals(connMethod.getName())
                                                            || "createStatement".equals(connMethod.getName())) {
                                                        executedStatements.incrementAndGet();
                                                    }
                                                    var statement = connMethod.invoke(connection, connArgs);
                                                    if ("prepareStatement".equals(connMethod.getName())
                                                            && connArgs != null
                                                            && connArgs.length > 0
                                                            && connArgs[0] instanceof String sql
                                                            && sql.contains("FROM reference.instrument_alias")) {
                                                        var aliasIds = new ArrayList<UUID>();
                                                        lastAliasQueryIds.set(aliasIds);
                                                        return java.lang.reflect.Proxy.newProxyInstance(
                                                                java.sql.PreparedStatement.class.getClassLoader(),
                                                                new Class<?>[] {java.sql.PreparedStatement.class},
                                                                (statementProxy, statementMethod, statementArgs) -> {
                                                                    if ("setObject".equals(statementMethod.getName())
                                                                            && statementArgs != null
                                                                            && statementArgs.length > 1
                                                                            && statementArgs[1] instanceof UUID id) {
                                                                        aliasIds.add(id);
                                                                    }
                                                                    return statementMethod.invoke(
                                                                            statement, statementArgs);
                                                                });
                                                    }
                                                    return statement;
                                                });
                                    }
                                    return method.invoke(dataSource, args);
                                });
                    }
                    return bean;
                }
            };
        }
    }
}
