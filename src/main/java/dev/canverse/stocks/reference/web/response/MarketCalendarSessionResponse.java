package dev.canverse.stocks.reference.web.response;

import dev.canverse.stocks.reference.domain.MarketSessionStatus;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record MarketCalendarSessionResponse(
        @NotNull LocalDate date,
        @NotNull MarketSessionStatus sessionStatus,
        LocalTime opensAt,
        LocalTime closesAt,
        @NotNull String sourceKind) {

    public static MarketCalendarSessionResponse from(ReferenceCatalogReadRepository.CalendarRow row) {
        return new MarketCalendarSessionResponse(
                row.date(), row.sessionStatus(), row.opensAt(), row.closesAt(), row.sourceKind());
    }
}
