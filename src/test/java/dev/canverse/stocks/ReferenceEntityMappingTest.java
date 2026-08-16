package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.reference.domain.AliasType;
import dev.canverse.stocks.reference.domain.Country;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.MarketCalendar;
import dev.canverse.stocks.reference.domain.MarketCalendarId;
import dev.canverse.stocks.reference.domain.MarketCurrencyId;
import dev.canverse.stocks.reference.domain.MarketSessionStatus;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.infrastructure.CurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentAliasRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentRepository;
import dev.canverse.stocks.reference.infrastructure.MarketCurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.MarketRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
class ReferenceEntityMappingTest {

    private static final UUID XIST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANUAL = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Instant T1 = Instant.parse("2026-08-16T09:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Autowired
    CurrencyRepository currencyRepository;

    @Autowired
    MarketRepository marketRepository;

    @Autowired
    MarketCurrencyRepository marketCurrencyRepository;

    @Autowired
    InstrumentRepository instrumentRepository;

    @Autowired
    InstrumentAliasRepository instrumentAliasRepository;

    @Test
    void allReferenceMappingsLoadStableAndCompositeIdentities() {
        var country = entityManager.find(Country.class, "TR");
        var currency = currencyRepository.findById("TRY").orElseThrow();
        var xist = marketRepository.findById(XIST).orElseThrow();
        var marketCurrency = marketCurrencyRepository
                .findById(new MarketCurrencyId(XIST, "TRY"))
                .orElseThrow();

        assertThat(country.getCode()).isEqualTo("TR");
        assertThat(currency.getMinorUnit()).isEqualTo((short) 2);
        assertThat(xist.getCountry().getCode()).isEqualTo("TR");
        assertThat(marketCurrency.getId().marketId()).isEqualTo(XIST);
        assertThat(marketCurrency.getId().currencyCode()).isEqualTo("TRY");
        assertThat(marketCurrency.isPrimaryQuote()).isTrue();
    }

    @Test
    void instrumentAliasAndCalendarMappingsLoadWithLazyReferences() {
        var userId = UUID.randomUUID();
        var instrumentId = UUID.randomUUID();
        var aliasId = UUID.randomUUID();
        var date = LocalDate.of(2026, 8, 17);
        var created = OffsetDateTime.ofInstant(T1, ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO identity.user_account (id, email, email_normalized, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                userId,
                "mapping-reference@example.com",
                "mapping-reference@example.com",
                created,
                created);
        jdbcTemplate.update(
                "INSERT INTO reference.instrument"
                        + " (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized,"
                        + " instrument_type, quotation_currency_code, valuation_method, source_kind, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                instrumentId,
                userId,
                MANUAL,
                "MAP-FUND",
                "MAP-FUND",
                "Mapping fund",
                "MAPPING FUND",
                "FUND",
                "TRY",
                "MANUAL_VALUE",
                "USER_ENTERED",
                created,
                created);
        jdbcTemplate.update(
                "INSERT INTO reference.instrument_alias"
                        + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                aliasId,
                instrumentId,
                "USER",
                "Map Alias",
                "MAP ALIAS",
                created);
        jdbcTemplate.update(
                "INSERT INTO reference.market_calendar"
                        + " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)"
                        + " VALUES (?, ?, 'OPEN', ?, ?, 'USER_ENTERED', ?)",
                MANUAL,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                created);

        entityManager.clear();

        var instrument = instrumentRepository.findById(instrumentId).orElseThrow();
        var alias = instrumentAliasRepository.findById(aliasId).orElseThrow();
        var calendar = entityManager.find(MarketCalendar.class, new MarketCalendarId(MANUAL, date));

        assertThat(instrument.getOwnerUserAccount().getId()).isEqualTo(userId);
        assertThat(instrument.getMarket().getId()).isEqualTo(MANUAL);
        assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.FUND);
        assertThat(instrument.getValuationMethod()).isEqualTo(ValuationMethod.MANUAL_VALUE);
        assertThat(instrument.getVersion()).isZero();
        assertThat(alias.getInstrument().getId()).isEqualTo(instrumentId);
        assertThat(alias.getAliasType()).isEqualTo(AliasType.USER);
        assertThat(calendar.getId().calendarDate()).isEqualTo(date);
        assertThat(calendar.getSessionStatus()).isEqualTo(MarketSessionStatus.OPEN);
        assertThat(calendar.getOpensAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(calendar.getClosesAt()).isEqualTo(LocalTime.of(17, 0));
    }
}
