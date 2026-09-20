package dev.canverse.stocks.investing.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.investing.application.PortfolioService;
import dev.canverse.stocks.investing.web.request.ArchivePortfolioRequest;
import dev.canverse.stocks.investing.web.request.CreatePortfolioRequest;
import dev.canverse.stocks.investing.web.request.UpdatePortfolioRequest;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import dev.canverse.stocks.investing.web.response.PortfolioSummaryResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody CreatePortfolioRequest request) {
        var response = portfolioService.create(identity.userAccountId(), request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/portfolios/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PortfolioSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return new ResponseEntity<>(portfolioService.list(identity.userAccountId(), includeArchived), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID portfolioId) {
        return new ResponseEntity<>(portfolioService.get(identity.userAccountId(), portfolioId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PutMapping("/{portfolioId}")
    public ResponseEntity<PortfolioResponse> update(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID portfolioId,
            @Valid @RequestBody UpdatePortfolioRequest request) {
        return new ResponseEntity<>(portfolioService.update(identity.userAccountId(), portfolioId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping("/{portfolioId}/archive")
    public ResponseEntity<PortfolioResponse> archive(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID portfolioId,
            @Valid @RequestBody ArchivePortfolioRequest request) {
        return new ResponseEntity<>(portfolioService.archive(identity.userAccountId(), portfolioId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }
}
