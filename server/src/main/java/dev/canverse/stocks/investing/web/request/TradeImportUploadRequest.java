package dev.canverse.stocks.investing.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@Schema(name = "TradeImportUploadRequest")
public record TradeImportUploadRequest(
        @NotNull UUID clientRequestId,
        @NotNull UUID accountId,
        @NotNull @Schema(type = "string", format = "binary") MultipartFile file
) {}
