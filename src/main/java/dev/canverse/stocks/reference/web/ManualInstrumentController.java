package dev.canverse.stocks.reference.web;

import dev.canverse.stocks.identity.application.AuthenticatedIdentityResolver;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.reference.application.InstrumentSearchService;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.input.ManualInstrumentCreateRequest;
import dev.canverse.stocks.reference.input.ManualInstrumentUpdateRequest;
import dev.canverse.stocks.reference.output.InstrumentPageResponse;
import dev.canverse.stocks.reference.output.InstrumentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference/instruments")
@Validated
@RequiredArgsConstructor
public class ManualInstrumentController {

    private final AuthenticatedIdentityResolver identityResolver;
    private final ManualInstrumentService manualInstrumentService;
    private final InstrumentSearchService searchService;

    @PostMapping
    public ResponseEntity<InstrumentResponse> create(
            Authentication authentication, @Valid @RequestBody ManualInstrumentCreateRequest request) {
        var identity = identityResolver.resolve(authentication);
        var response = manualInstrumentService.create(identity.userAccountId(), request);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.CREATED);
    }

    @PutMapping("{instrumentId}")
    public ResponseEntity<InstrumentResponse> update(
            Authentication authentication,
            @PathVariable UUID instrumentId,
            @Valid @RequestBody ManualInstrumentUpdateRequest request) {
        var identity = identityResolver.resolve(authentication);
        var response = manualInstrumentService.update(identity.userAccountId(), instrumentId, request);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("{instrumentId}")
    public ResponseEntity<InstrumentResponse> get(Authentication authentication, @PathVariable UUID instrumentId) {
        var identity = identityResolver.resolve(authentication);
        return new ResponseEntity<>(
                manualInstrumentService.get(identity.userAccountId(), instrumentId),
                CacheHeaders.noStore(),
                HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<InstrumentPageResponse> search(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID marketId,
            @RequestParam(required = false) InstrumentType type,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "" + InstrumentSearchService.DEFAULT_LIMIT)
                    @Min(InstrumentSearchService.MIN_LIMIT)
                    @Max(InstrumentSearchService.MAX_LIMIT)
                    int limit,
            @RequestParam(required = false) String cursor) {
        var identity = identityResolver.resolve(authentication);
        var response =
                searchService.search(identity.userAccountId(), query, marketId, type, includeInactive, limit, cursor);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }
}
