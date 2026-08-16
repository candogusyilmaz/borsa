package dev.canverse.stocks.reference.application;

import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.domain.CurrencyCode;
import dev.canverse.stocks.reference.domain.Instrument;
import dev.canverse.stocks.reference.domain.InstrumentAlias;
import dev.canverse.stocks.reference.domain.InstrumentSymbol;
import dev.canverse.stocks.reference.domain.MarketCurrencyId;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import dev.canverse.stocks.reference.infrastructure.CurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentAliasRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentRepository;
import dev.canverse.stocks.reference.infrastructure.MarketCurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.MarketRepository;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import dev.canverse.stocks.reference.web.request.InstrumentAliasInput;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import dev.canverse.stocks.reference.web.request.ManualInstrumentUpdateRequest;
import dev.canverse.stocks.reference.web.response.InstrumentResponse;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualInstrumentService {

    private final EntityManager entityManager;
    private final MarketRepository marketRepository;
    private final CurrencyRepository currencyRepository;
    private final MarketCurrencyRepository marketCurrencyRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentAliasRepository instrumentAliasRepository;
    private final ReferenceCatalogReadRepository readRepository;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @Transactional
    public InstrumentResponse create(UUID ownerUserAccountId, ManualInstrumentCreateRequest request) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(request, "request");
        var symbol = InstrumentSymbol.of(request.symbol());
        var name = request.name().trim();
        var quotationCurrency = CurrencyCode.of(request.quotationCurrency());
        var market = marketRepository
                .findById(request.marketId())
                .orElseThrow(() -> new AppException(ReferenceErrorCode.MARKET_NOT_FOUND));
        if (!market.isActive()) {
            throw new AppException(ReferenceErrorCode.INACTIVE_REFERENCE);
        }
        var currencyEntity = currencyRepository
                .findById(quotationCurrency.value())
                .orElseThrow(() -> new AppException(ReferenceErrorCode.CURRENCY_NOT_FOUND));
        if (!currencyEntity.isActive()) {
            throw new AppException(ReferenceErrorCode.INACTIVE_REFERENCE);
        }
        if (!marketCurrencyRepository.existsById(new MarketCurrencyId(market.getId(), quotationCurrency.value()))) {
            throw new AppException(ReferenceErrorCode.UNSUPPORTED_MARKET_CURRENCY);
        }

        var owner = entityManager.getReference(UserAccount.class, ownerUserAccountId);
        var observedAt = clock.instant();
        var instrument = Instrument.manual(
                idGenerator.next(),
                owner,
                market,
                symbol,
                name,
                request.instrumentType(),
                quotationCurrency,
                request.valuationMethod(),
                observedAt);
        var savedInstrument = instrumentRepository.save(instrument);
        instrumentAliasRepository.saveAll(createAliases(savedInstrument, request.aliases(), observedAt));
        // The JDBC read model below uses a separate connection and must see the aggregate before returning.
        instrumentRepository.flush();
        instrumentAliasRepository.flush();
        return readRepository
                .findVisibleInstrument(ownerUserAccountId, instrument.getId())
                .map(InstrumentResponse::from)
                .orElseThrow(() -> new IllegalStateException("Created instrument was not readable"));
    }

    @Transactional
    public InstrumentResponse update(
            UUID ownerUserAccountId, UUID instrumentId, ManualInstrumentUpdateRequest request) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(request, "request");
        var instrument = instrumentRepository
                .findOwnedById(instrumentId, ownerUserAccountId)
                .orElseThrow(() -> new AppException(ReferenceErrorCode.INSTRUMENT_NOT_FOUND));
        if (instrument.getVersion() != request.version()) {
            throw new AppException(ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT);
        }
        var name = request.name().trim();
        var observedAt = clock.instant();
        var metadataChanged = !Objects.equals(instrument.getName(), name)
                || instrument.getValuationMethod() != request.valuationMethod()
                || instrument.isActive() != request.active()
                || !Objects.equals(instrument.getUpdatedAt(), observedAt);
        if (metadataChanged) {
            instrument.updateMetadata(name, request.valuationMethod(), request.active(), observedAt);
            entityManager.flush();
        } else {
            forceAliasAggregateVersion(instrumentId, ownerUserAccountId, request.version());
        }
        instrumentAliasRepository.deleteByInstrumentId(instrumentId);
        var managedInstrument = instrumentRepository.getReferenceById(instrumentId);
        instrumentAliasRepository.saveAll(createAliases(managedInstrument, request.aliases(), observedAt));
        // The replacement must be visible to the explicit response projection in this transaction.
        instrumentAliasRepository.flush();
        return readRepository
                .findVisibleInstrument(ownerUserAccountId, instrumentId)
                .map(InstrumentResponse::from)
                .orElseThrow(() -> new AppException(ReferenceErrorCode.INSTRUMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public InstrumentResponse get(UUID ownerUserAccountId, UUID instrumentId) {
        return readRepository
                .findVisibleInstrument(ownerUserAccountId, instrumentId)
                .map(InstrumentResponse::from)
                .orElseThrow(() -> new AppException(ReferenceErrorCode.INSTRUMENT_NOT_FOUND));
    }

    private List<InstrumentAlias> createAliases(
            Instrument instrument, List<InstrumentAliasInput> aliases, Instant observedAt) {
        return aliases.stream()
                .map(alias -> {
                    var value = alias.value().trim();
                    return InstrumentAlias.create(
                            idGenerator.next(),
                            instrument,
                            alias.type(),
                            value,
                            value.toUpperCase(Locale.ROOT),
                            observedAt);
                })
                .toList();
    }

    private void forceAliasAggregateVersion(UUID instrumentId, UUID ownerUserAccountId, long expectedVersion) {
        var updated = instrumentRepository.incrementOwnedVersion(instrumentId, ownerUserAccountId, expectedVersion);
        if (updated != 1) {
            throw new AppException(ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT);
        }
    }
}
