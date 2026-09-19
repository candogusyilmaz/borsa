package dev.canverse.stocks.investing.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.investing.application.InvestingQueryService;
import dev.canverse.stocks.investing.web.response.PositionResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.SliceResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investing/positions")
@RequiredArgsConstructor
public class PositionController {

    private static final int DEFAULT_LIMIT = 50;

    private final InvestingQueryService queryService;

    @GetMapping
    public ResponseEntity<SliceResponse<PositionResponse>> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) UUID accountId,
            @PageableDefault(size = DEFAULT_LIMIT, sort = "accountName", direction = Sort.Direction.ASC) Pageable pageable) {
        return new ResponseEntity<>(queryService.listOpenPositions(identity.userAccountId(), accountId, pageable), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("/{accountId}/{instrumentId}")
    public ResponseEntity<PositionResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @PathVariable UUID instrumentId) {
        return new ResponseEntity<>(queryService.getPosition(identity.userAccountId(), accountId, instrumentId), CacheHeaders.noStore(), HttpStatus.OK);
    }
}
