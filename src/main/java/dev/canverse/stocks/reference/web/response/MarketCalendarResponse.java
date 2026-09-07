package dev.canverse.stocks.reference.web.response;

import dev.canverse.stocks.reference.domain.CalendarCoverageStatus;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MarketCalendarResponse(@NotNull UUID marketId, @NotNull String marketCode, @NotNull String timeZone, @NotNull LocalDate from,
        @NotNull LocalDate to, @NotNull CalendarCoverageStatus coverageStatus, @NotNull List<MarketCalendarSessionResponse> sessions,
        @NotNull List<LocalDate> missingDates) {

    public MarketCalendarResponse {
        sessions = List.copyOf(sessions);
        missingDates = List.copyOf(missingDates);
    }

    public static MarketCalendarResponse from(ReferenceCatalogReadRepository.MarketCalendarHeader header, LocalDate from, LocalDate to,
            CalendarCoverageStatus coverageStatus, List<ReferenceCatalogReadRepository.CalendarRow> rows, List<LocalDate> missingDates) {
        return new MarketCalendarResponse(header.id(), header.code(), header.timeZone(), from, to, coverageStatus,
                rows.stream().map(MarketCalendarSessionResponse::from).toList(), missingDates);
    }
}
