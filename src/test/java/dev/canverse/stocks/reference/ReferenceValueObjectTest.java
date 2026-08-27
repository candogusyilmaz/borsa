package dev.canverse.stocks.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.reference.domain.AliasType;
import dev.canverse.stocks.reference.domain.CalendarCoverageStatus;
import dev.canverse.stocks.reference.domain.CountryCode;
import dev.canverse.stocks.reference.domain.CurrencyCode;
import dev.canverse.stocks.reference.domain.Instrument;
import dev.canverse.stocks.reference.domain.InstrumentSymbol;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.Market;
import dev.canverse.stocks.reference.domain.MarketCode;
import dev.canverse.stocks.reference.domain.MarketSessionStatus;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReferenceValueObjectTest {

    @Test
    void stableCodesRequireAlreadyCanonicalUppercaseForms() {
        assertThat(CountryCode.of("TR").value()).isEqualTo("TR");
        assertThat(CurrencyCode.of("TRY").code()).isEqualTo("TRY");
        assertThat(MarketCode.of("XIST").code()).isEqualTo("XIST");

        assertThatThrownBy(() -> CountryCode.of(" tr ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CountryCode.of("TR ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CountryCode.of("tr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CountryCode.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrencyCode.of("usd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrencyCode.of("USD ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrencyCode.of("US")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketCode.of("manual ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketCode.of("manual")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketCode.of("M".repeat(33))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CountryCode.of("TUR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instrumentSymbolTrimsDisplayAndUsesLocaleRootNormalization() {
        var previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var symbol = InstrumentSymbol.of(" i.test ");

            assertThat(symbol.value()).isEqualTo("i.test");
            assertThat(symbol.normalized()).isEqualTo("I.TEST");
            assertThat(InstrumentSymbol.of("S".repeat(32)).value()).hasSize(32);
            assertThatThrownBy(() -> InstrumentSymbol.of("S".repeat(33))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InstrumentSymbol.of("bad symbol")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InstrumentSymbol.of(" ")).isInstanceOf(IllegalArgumentException.class);
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void applicationEnumsSerializeAndParseOnlyExactAcceptedCodes() throws Exception {
        var mapper = JsonMapper.builder().build();

        assertEnumJson(mapper, AliasType.USER, AliasType.class, "USER");
        assertEnumJson(mapper, CalendarCoverageStatus.PARTIAL, CalendarCoverageStatus.class, "PARTIAL");
        assertEnumJson(mapper, InstrumentType.CASH_EQUIVALENT, InstrumentType.class, "CASH_EQUIVALENT");
        assertEnumJson(mapper, MarketSessionStatus.OPEN, MarketSessionStatus.class, "OPEN");
        assertEnumJson(mapper, ValuationMethod.MANUAL_VALUE, ValuationMethod.class, "MANUAL_VALUE");

        assertThatThrownBy(() -> mapper.readValue("\"equity\"", InstrumentType.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("\"UNKNOWN\"", AliasType.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void instrumentConstructionAndUpdatePreserveImmutableIdentity() {
        var id = UUID.randomUUID();
        var owner = mock(UserAccount.class);
        var market = mock(Market.class);
        var createdAt = Instant.parse("2026-08-16T09:00:00Z");
        var instrument = Instrument.manual(
                id,
                owner,
                market,
                InstrumentSymbol.of("owner-fund"),
                "Owner fund",
                InstrumentType.FUND,
                CurrencyCode.of("GBP"),
                ValuationMethod.MANUAL_VALUE,
                createdAt);

        assertThat(instrument.getId()).isEqualTo(id);
        assertThat(instrument.getOwnerUserAccount()).isSameAs(owner);
        assertThat(instrument.getMarket()).isSameAs(market);
        assertThat(instrument.getSymbol()).isEqualTo("owner-fund");
        assertThat(instrument.getSymbolNormalized()).isEqualTo("OWNER-FUND");
        assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.FUND);
        assertThat(instrument.getQuotationCurrencyCode()).isEqualTo("GBP");
        assertThat(instrument.getSourceKind()).isEqualTo("USER_ENTERED");
        assertThat(instrument.isActive()).isTrue();
        assertThat(instrument.getVersion()).isZero();
        assertThat(instrument.getCreatedAt()).isEqualTo(createdAt);

        instrument.updateMetadata("Renamed fund", ValuationMethod.NOT_VALUED, false, createdAt.plusSeconds(1));

        assertThat(instrument.getName()).isEqualTo("Renamed fund");
        assertThat(instrument.getNameNormalized()).isEqualTo("RENAMED FUND");
        assertThat(instrument.getValuationMethod()).isEqualTo(ValuationMethod.NOT_VALUED);
        assertThat(instrument.isActive()).isFalse();
        assertThat(instrument.getId()).isEqualTo(id);
        assertThat(instrument.getOwnerUserAccount()).isSameAs(owner);
        assertThat(instrument.getMarket()).isSameAs(market);
        assertThat(instrument.getSymbol()).isEqualTo("owner-fund");
        assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.FUND);
        assertThat(instrument.getQuotationCurrencyCode()).isEqualTo("GBP");
        assertThat(instrument.getSourceKind()).isEqualTo("USER_ENTERED");
        assertThat(instrument.getCreatedAt()).isEqualTo(createdAt);

        assertThatThrownBy(() -> Instrument.manual(
                        id,
                        null,
                        market,
                        InstrumentSymbol.of("fund"),
                        "Fund",
                        InstrumentType.FUND,
                        CurrencyCode.of("GBP"),
                        ValuationMethod.MANUAL_VALUE,
                        createdAt))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void timezoneValidationIsExplicitAndIndependentOfTheMachineDefault() {
        assertThat(ZoneId.of("UTC")).isEqualTo(ZoneId.of("UTC"));
        assertThat(ZoneId.of("Europe/Istanbul")).isEqualTo(ZoneId.of("Europe/Istanbul"));
        assertThatThrownBy(() -> ZoneId.of("not/a-zone")).isInstanceOf(DateTimeException.class);
    }

    private static <E extends Enum<E>> void assertEnumJson(JsonMapper mapper, E value, Class<E> enumType, String code)
            throws Exception {
        assertThat(mapper.writeValueAsString(value)).isEqualTo("\"" + code + "\"");
        assertThat(mapper.readValue("\"" + code + "\"", enumType)).isEqualTo(value);
    }
}
