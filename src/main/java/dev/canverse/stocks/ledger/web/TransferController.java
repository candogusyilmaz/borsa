package dev.canverse.stocks.ledger.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.ledger.application.CashTransferService;
import dev.canverse.stocks.ledger.web.request.TransferPreviewRequest;
import dev.canverse.stocks.ledger.web.request.TransferRequest;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.ledger.web.response.TransferPreviewResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final CashTransferService transferService;

    @PostMapping("/previews")
    public ResponseEntity<TransferPreviewResponse> preview(
            @AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @RequestBody TransferPreviewRequest request) {
        return new ResponseEntity<>(
                transferService.preview(identity.userAccountId(), request), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> transfer(
            @AuthenticationPrincipal AuthenticatedIdentity identity, @Valid @RequestBody TransferRequest request) {
        var response = transferService.transfer(identity.userAccountId(), request);
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create("/api/v1/activities/" + response.id()));
        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }
}
