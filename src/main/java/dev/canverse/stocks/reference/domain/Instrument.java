package dev.canverse.stocks.reference.domain;

import dev.canverse.stocks.identity.domain.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "instrument", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    public static final String USER_ENTERED_SOURCE_KIND = "USER_ENTERED";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_account_id")
    private UserAccount ownerUserAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id")
    private Market market;

    private String symbol;

    private String symbolNormalized;

    private String name;

    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    private InstrumentType instrumentType;

    private String quotationCurrencyCode;

    @Enumerated(EnumType.STRING)
    private ValuationMethod valuationMethod;

    private boolean active;

    private String sourceKind;

    @Version
    private long version;

    private Instant createdAt;

    private Instant updatedAt;

    public static Instrument manual(
            UUID id,
            UserAccount owner,
            Market market,
            InstrumentSymbol symbol,
            String name,
            InstrumentType instrumentType,
            CurrencyCode quotationCurrency,
            ValuationMethod valuationMethod,
            Instant observedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(instrumentType, "instrumentType");
        Objects.requireNonNull(quotationCurrency, "quotationCurrency");
        Objects.requireNonNull(valuationMethod, "valuationMethod");
        Objects.requireNonNull(observedAt, "observedAt");

        var instrument = new Instrument();
        instrument.id = id;
        instrument.ownerUserAccount = owner;
        instrument.market = market;
        instrument.symbol = symbol.value();
        instrument.symbolNormalized = symbol.normalized();
        var displayName = name.trim();
        if (!ManualInstrumentConstraints.fitsDisplayAndNormalizedBounds(
                displayName, ManualInstrumentConstraints.MAX_NAME_LENGTH)) {
            throw new IllegalArgumentException("Instrument name exceeds its display or normalized length bound");
        }
        instrument.name = displayName;
        instrument.nameNormalized = displayName.toUpperCase(Locale.ROOT);
        instrument.instrumentType = instrumentType;
        instrument.quotationCurrencyCode = quotationCurrency.value();
        instrument.valuationMethod = valuationMethod;
        instrument.active = true;
        instrument.sourceKind = USER_ENTERED_SOURCE_KIND;
        instrument.createdAt = observedAt;
        instrument.updatedAt = observedAt;
        return instrument;
    }

    public void updateMetadata(String name, ValuationMethod valuationMethod, boolean active, Instant observedAt) {
        if (ownerUserAccount == null || !USER_ENTERED_SOURCE_KIND.equals(sourceKind)) {
            throw new IllegalStateException("Only owner-entered instruments can be updated");
        }
        var displayName = Objects.requireNonNull(name, "name").trim();
        if (!ManualInstrumentConstraints.fitsDisplayAndNormalizedBounds(
                displayName, ManualInstrumentConstraints.MAX_NAME_LENGTH)) {
            throw new IllegalArgumentException("Instrument name exceeds its display or normalized length bound");
        }
        this.name = displayName;
        this.nameNormalized = displayName.toUpperCase(Locale.ROOT);
        this.valuationMethod = Objects.requireNonNull(valuationMethod, "valuationMethod");
        this.active = active;
        this.updatedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
}
