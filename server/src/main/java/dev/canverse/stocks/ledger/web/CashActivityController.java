package dev.canverse.stocks.ledger.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.ledger.application.CashActivityCommandService;
import dev.canverse.stocks.ledger.application.CashActivityQueryService;
import dev.canverse.stocks.ledger.web.request.CashActivityRequest;
import dev.canverse.stocks.ledger.web.request.ReversalRequest;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.SliceResponse;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CashActivityController {

    private static final int DEFAULT_LIMIT = 50;
    private final CashActivityCommandService commandService;
    private final CashActivityQueryService queryService;

    @PostMapping("/api/v1/accounts/{accountId}/activities")
    public ResponseEntity<ActivityResponse> record(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @Valid @RequestBody CashActivityRequest request) {
        var response = commandService.recordCashActivity(identity.userAccountId(), accountId, request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/activities/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/activities")
    public ResponseEntity<SliceResponse<ActivityResponse>> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) UUID accountId,
            @PageableDefault(size = DEFAULT_LIMIT, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new ResponseEntity<>(queryService.list(identity.userAccountId(), accountId, pageable), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("/api/v1/activities/{activityId}")
    public ResponseEntity<ActivityResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID activityId) {
        return new ResponseEntity<>(queryService.get(identity.userAccountId(), activityId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping("/api/v1/activities/{activityId}/reversals")
    public ResponseEntity<ActivityResponse> reverse(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID activityId,
            @Valid @RequestBody ReversalRequest request) {
        var response = commandService.reverse(identity.userAccountId(), activityId, request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/activities/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }
}
