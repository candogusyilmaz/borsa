package dev.canverse.stocks.investing.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.investing.application.InvestingQueryService;
import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.investing.web.response.TradePreviewResponse;
import dev.canverse.stocks.investing.web.response.TradeResponse;
import dev.canverse.stocks.investing.web.response.TradeSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class InvestingTradeController {

    private static final int DEFAULT_LIMIT = 50;

    private final InvestingTradeCommandService commandService;
    private final InvestingQueryService queryService;

    @PostMapping("/previews")
    public ResponseEntity<TradePreviewResponse> preview(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody TradePreviewRequest request) {
        return new ResponseEntity<>(commandService.preview(identity.userAccountId(), request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<TradeResponse> commit(@AuthenticationPrincipal AuthenticatedIdentity identity, @Valid @RequestBody TradeCommitRequest request) {
        var response = commandService.commit(identity.userAccountId(), request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/trades/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @GetMapping("/{activityId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<TradeResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID activityId) {
        return new ResponseEntity<>(queryService.getTrade(identity.userAccountId(), activityId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<SliceResponse<TradeSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(required = false) UUID accountId, @RequestParam(required = false) UUID instrumentId, @RequestParam(required = false) UUID portfolioId,
            @PageableDefault(size = DEFAULT_LIMIT, sort = "effectiveAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new ResponseEntity<>(queryService.listTrades(identity.userAccountId(), accountId, instrumentId, portfolioId, pageable), CacheHeaders.noStore(),
                HttpStatus.OK);
    }
}
