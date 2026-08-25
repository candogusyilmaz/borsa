package dev.canverse.stocks.ledger.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.ledger.application.ReconciliationCommandService;
import dev.canverse.stocks.ledger.application.ReconciliationReadService;
import dev.canverse.stocks.ledger.web.request.ReconciliationCommitRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationCorrectionRequest;
import dev.canverse.stocks.ledger.web.request.ReconciliationPreviewRequest;
import dev.canverse.stocks.ledger.web.response.ReconciliationPageResponse;
import dev.canverse.stocks.ledger.web.response.ReconciliationPreviewResponse;
import dev.canverse.stocks.ledger.web.response.ReconciliationResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReconciliationController {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final ReconciliationCommandService commandService;
    private final ReconciliationReadService readService;

    @PostMapping("/api/v1/accounts/{accountId}/reconciliation-previews")
    public ResponseEntity<ReconciliationPreviewResponse> preview(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID accountId,
            @Valid @RequestBody ReconciliationPreviewRequest request) {
        request.validate();
        return new ResponseEntity<>(
                commandService.preview(identity.userAccountId(), accountId, request),
                CacheHeaders.noStore(),
                HttpStatus.OK);
    }

    @PostMapping("/api/v1/accounts/{accountId}/reconciliations")
    public ResponseEntity<ReconciliationResponse> commit(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID accountId,
            @Valid @RequestBody ReconciliationCommitRequest request) {
        request.validate();
        var response = commandService.commit(identity.userAccountId(), accountId, request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/reconciliations/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/accounts/{accountId}/reconciliations")
    public ResponseEntity<ReconciliationPageResponse> list(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return new ResponseEntity<>(
                readService.list(identity.userAccountId(), accountId, limit, cursor),
                CacheHeaders.noStore(),
                HttpStatus.OK);
    }

    @GetMapping("/api/v1/reconciliations/{reconciliationId}")
    public ResponseEntity<ReconciliationResponse> detail(
            @AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID reconciliationId) {
        return new ResponseEntity<>(
                readService.detail(identity.userAccountId(), reconciliationId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping("/api/v1/reconciliations/{reconciliationId}/corrections")
    public ResponseEntity<ReconciliationResponse> correct(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @PathVariable UUID reconciliationId,
            @Valid @RequestBody ReconciliationCorrectionRequest request) {
        request.validate();
        var response = commandService.correct(identity.userAccountId(), reconciliationId, request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/reconciliations/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }
}
