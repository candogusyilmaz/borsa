package dev.canverse.stocks.investing.web;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.investing.application.TradeImportService;
import dev.canverse.stocks.investing.web.request.TradeImportCommitRequest;
import dev.canverse.stocks.investing.web.request.TradeImportUploadRequest;
import dev.canverse.stocks.investing.web.response.TradeImportCommitResponse;
import dev.canverse.stocks.investing.web.response.TradeImportPreviewResponse;
import dev.canverse.stocks.investing.web.response.TradeImportUploadResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class TradeImportController {

    private final TradeImportService tradeImportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a funded-trade CSV", description = "Stores a bounded FUNDED_TRADE_CSV_V1 file for owner-scoped preview.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = TradeImportUploadRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "A new import batch was created",
                    headers = @Header(name = "Location", description = "The preview URL for the created batch"),
                    content = @Content(schema = @Schema(implementation = TradeImportUploadResponse.class))),
            @ApiResponse(responseCode = "200", description = "An existing batch with the same account and file content was returned",
                    headers = @Header(name = "Location", description = "The preview URL for the existing batch"),
                    content = @Content(schema = @Schema(implementation = TradeImportUploadResponse.class)))})
    public ResponseEntity<TradeImportUploadResponse> upload(@AuthenticationPrincipal AuthenticatedIdentity identity,
            @Valid @ModelAttribute TradeImportUploadRequest request) {
        var response = tradeImportService.upload(identity.userAccountId(), request.clientRequestId(), request.accountId(), request.file());
        var headers = CacheHeaders.noStore();
        headers.setLocation(URI.create(response.previewUrl()));
        return new ResponseEntity<>(response, headers, response.duplicateContent() ? HttpStatus.OK : HttpStatus.CREATED);
    }

    @GetMapping("/{batchId:[0-9a-fA-F-]{36}}/preview")
    @Operation(summary = "Preview an import batch", description = "Returns stored normalization evidence and a repeatable current-state simulation.")
    @ApiResponse(responseCode = "200", description = "The current import preview",
            content = @Content(schema = @Schema(implementation = TradeImportPreviewResponse.class)))
    public ResponseEntity<TradeImportPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID batchId) {
        return new ResponseEntity<>(tradeImportService.preview(identity.userAccountId(), batchId), CacheHeaders.noStore(), HttpStatus.OK);
    }

    @PostMapping(value = "/{batchId:[0-9a-fA-F-]{36}}/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Commit a reviewed import batch",
            description = "Revalidates the confirmed preview under deterministic financial locks and posts the batch atomically.")
    @ApiResponse(responseCode = "200", description = "The committed batch or an exact commit retry",
            content = @Content(schema = @Schema(implementation = TradeImportCommitResponse.class)))
    public ResponseEntity<TradeImportCommitResponse> commit(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID batchId,
            @Valid @RequestBody TradeImportCommitRequest request) {
        return new ResponseEntity<>(tradeImportService.commit(identity.userAccountId(), batchId, request), CacheHeaders.noStore(), HttpStatus.OK);
    }
}
