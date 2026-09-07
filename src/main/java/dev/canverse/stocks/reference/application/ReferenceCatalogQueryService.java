package dev.canverse.stocks.reference.application;

import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.reference.domain.CalendarCoverageStatus;
import dev.canverse.stocks.reference.error.ReferenceErrorCode;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import dev.canverse.stocks.reference.web.response.CountryResponse;
import dev.canverse.stocks.reference.web.response.CurrencyResponse;
import dev.canverse.stocks.reference.web.response.MarketCalendarResponse;
import dev.canverse.stocks.reference.web.response.MarketResponse;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceCatalogQueryService {

    private static final int MAX_CALENDAR_RANGE_DAYS = 365;
    private static final int MAX_CALENDAR_DATES = MAX_CALENDAR_RANGE_DAYS + 1;

    private final ReferenceCatalogReadRepository readRepository;

    @Transactional(readOnly = true)
    public List<CountryResponse> countries() {
        return List.copyOf(readRepository.findActiveCountries());
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> currencies() {
        return List.copyOf(readRepository.findActiveCurrencies());
    }

    @Transactional(readOnly = true)
    public List<MarketResponse> markets() {
        var markets = List.copyOf(readRepository.findActiveMarkets());
        markets.forEach(market -> validateTimeZone(market.timeZone()));
        return markets;
    }

    @Transactional(readOnly = true)
    public MarketCalendarResponse calendar(UUID marketId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        var header = readRepository.findActiveMarket(Objects.requireNonNull(marketId, "marketId"))
                .orElseThrow(() -> new AppException(ReferenceErrorCode.MARKET_NOT_FOUND));
        validateTimeZone(header.timeZone());

        var storedRows = readRepository.findCalendarRows(marketId, from, to);
        var storedDates = storedRows.stream().map(ReferenceCatalogReadRepository.CalendarRow::date).toList();
        var storedDateSet = Set.copyOf(storedDates);
        var missingDates = from.datesUntil(to.plusDays(1)).filter(date -> !storedDateSet.contains(date)).toList();
        var coverage = storedRows.isEmpty() ? CalendarCoverageStatus.NONE
                : missingDates.isEmpty() ? CalendarCoverageStatus.COMPLETE : CalendarCoverageStatus.PARTIAL;
        return MarketCalendarResponse.from(header, from, to, coverage, storedRows, missingDates);
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_RANGE_DAYS) {
            throw ValidationErrors.invalidField("from/to", "error.fields.reference.invalid_value",
                    "The calendar range must be ordered and contain at most " + MAX_CALENDAR_DATES + " dates.");
        }
    }

    private static ZoneId validateTimeZone(String timeZone) {
        try {
            return ZoneId.of(Objects.requireNonNull(timeZone, "timeZone"));
        } catch (DateTimeException exception) {
            throw new IllegalStateException("Reference market has an invalid IANA timezone", exception);
        }
    }
}
