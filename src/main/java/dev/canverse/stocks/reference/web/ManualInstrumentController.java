package dev.canverse.stocks.reference.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.SliceResponse;
import dev.canverse.stocks.reference.application.InstrumentSearchService;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.application.model.InstrumentSearchCriteria;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import dev.canverse.stocks.reference.web.request.ManualInstrumentUpdateRequest;
import dev.canverse.stocks.reference.web.response.InstrumentResponse;
import dev.canverse.stocks.reference.web.response.InstrumentSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequiredArgsConstructor
public class ManualInstrumentController {

    private final ManualInstrumentService manualInstrumentService;
    private final InstrumentSearchService searchService;

    @PostMapping
    public ResponseEntity<InstrumentResponse> create(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody ManualInstrumentCreateRequest request) {
        request.validate();
        var response = manualInstrumentService.create(identity.userAccountId(), request);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.CREATED);
    }

    @PutMapping("{instrumentId}")
    public ResponseEntity<InstrumentResponse> update(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID instrumentId,
            @Valid @RequestBody ManualInstrumentUpdateRequest request) {
        request.validate();
        var response = manualInstrumentService.update(identity.userAccountId(), instrumentId, request);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("{instrumentId}")
    public ResponseEntity<InstrumentResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID instrumentId) {
        return new ResponseEntity<>(manualInstrumentService.get(identity.userAccountId(), instrumentId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<SliceResponse<InstrumentSummaryResponse>> search(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) String query, @RequestParam(required = false) UUID marketId, @RequestParam(required = false) InstrumentType type,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        var criteria = new InstrumentSearchCriteria(query, marketId, type, includeInactive);
        var response = searchService.search(identity.userAccountId(), criteria, pageable);
        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }
}
