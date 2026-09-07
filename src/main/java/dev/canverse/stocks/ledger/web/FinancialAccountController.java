package dev.canverse.stocks.ledger.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.application.FinancialAccountQueryService;
import dev.canverse.stocks.ledger.application.FinancialAccountSettingsService;
import dev.canverse.stocks.ledger.web.request.AccountMetadataRequest;
import dev.canverse.stocks.ledger.web.request.AccountPolicyRequest;
import dev.canverse.stocks.ledger.web.request.ArchiveAccountRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningCorrectionRequest;
import dev.canverse.stocks.ledger.web.response.BalanceResponse;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class FinancialAccountController {

    private final FinancialAccountOnboardingService onboardingService;
    private final FinancialAccountQueryService queryService;
    private final FinancialAccountSettingsService settingsService;
    private final FinancialAccountLifecycleService lifecycleService;

    @PostMapping
    public ResponseEntity<FinancialAccountResponse> create(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody CreateFinancialAccountRequest request) {
        request.validate();
        var response = onboardingService.create(identity.userAccountId(), request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create(ServletUriComponentsBuilder.fromCurrentRequest().path("/{accountId}").buildAndExpand(response.id()).toUriString()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<FinancialAccountResponse>> list(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return new ResponseEntity<>(queryService.list(identity.userAccountId(), includeArchived), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<FinancialAccountResponse> get(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId) {
        return new ResponseEntity<>(queryService.get(identity.userAccountId(), accountId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<FinancialAccountResponse> updateMetadata(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @Valid @RequestBody AccountMetadataRequest request) {
        return new ResponseEntity<>(settingsService.updateMetadata(identity.userAccountId(), accountId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PutMapping("/{accountId}/policy")
    public ResponseEntity<FinancialAccountResponse> updatePolicy(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @Valid @RequestBody AccountPolicyRequest request) {
        return new ResponseEntity<>(settingsService.updatePolicy(identity.userAccountId(), accountId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping("/{accountId}/archive")
    public ResponseEntity<FinancialAccountResponse> archive(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @Valid @RequestBody ArchiveAccountRequest request) {
        return new ResponseEntity<>(lifecycleService.archive(identity.userAccountId(), accountId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> balance(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @RequestParam(required = false) Instant asOf) {
        return new ResponseEntity<>(queryService.balance(identity.userAccountId(), accountId, asOf), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PutMapping("/{accountId}/opening-state")
    public ResponseEntity<FinancialAccountResponse> correctOpening(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID accountId,
            @Valid @RequestBody OpeningCorrectionRequest request) {
        return new ResponseEntity<>(lifecycleService.correctOpening(identity.userAccountId(), accountId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }
}
