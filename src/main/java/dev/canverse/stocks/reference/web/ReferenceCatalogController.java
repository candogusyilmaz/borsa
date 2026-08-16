package dev.canverse.stocks.reference.web;

import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.reference.application.ReferenceCatalogQueryService;
import dev.canverse.stocks.reference.output.CountryResponse;
import dev.canverse.stocks.reference.output.CurrencyResponse;
import dev.canverse.stocks.reference.output.MarketCalendarResponse;
import dev.canverse.stocks.reference.output.MarketResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference")
@RequiredArgsConstructor
public class ReferenceCatalogController {

    private final ReferenceCatalogQueryService queryService;

    @GetMapping("countries")
    public ResponseEntity<List<CountryResponse>> countries() {
        return new ResponseEntity<>(queryService.countries(), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("currencies")
    public ResponseEntity<List<CurrencyResponse>> currencies() {
        return new ResponseEntity<>(queryService.currencies(), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("markets")
    public ResponseEntity<List<MarketResponse>> markets() {
        return new ResponseEntity<>(queryService.markets(), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("markets/{marketId}/calendar")
    public ResponseEntity<MarketCalendarResponse> calendar(
            @PathVariable UUID marketId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ResponseEntity<>(queryService.calendar(marketId, from, to), CacheHeaders.noStore(), HttpStatus.OK);
    }
}
