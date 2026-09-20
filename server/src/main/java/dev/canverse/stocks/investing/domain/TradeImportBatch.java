package dev.canverse.stocks.investing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade_import_batch", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeImportBatch {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID financialAccountId;

    @Enumerated(EnumType.STRING)
    private TradeImportFormat importFormat;

    @Enumerated(EnumType.STRING)
    private TradeImportStatus status;

    private String originalFileName;
    private String mediaType;
    private long byteSize;
    private String contentSha256;
    private int parsedRowCount;
    private Instant createdAt;
    private Instant committedAt;

    @Version
    private long version;

    public static TradeImportBatch parsed(UUID id, UUID ownerUserAccountId, UUID financialAccountId, String originalFileName, String mediaType, long byteSize,
            String contentSha256, int parsedRowCount, Instant createdAt) {
        var batch = new TradeImportBatch();
        batch.id = Objects.requireNonNull(id, "id");
        batch.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        batch.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        batch.importFormat = TradeImportFormat.FUNDED_TRADE_CSV_V1;
        batch.status = TradeImportStatus.PARSED;
        batch.originalFileName = Objects.requireNonNull(originalFileName, "originalFileName");
        batch.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        batch.byteSize = byteSize;
        batch.contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256");
        batch.parsedRowCount = parsedRowCount;
        batch.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return batch;
    }

    public void markCommitted(Instant committedAt) {
        if (status != TradeImportStatus.PARSED) {
            throw new IllegalStateException("Only a parsed import batch can be committed");
        }
        var value = Objects.requireNonNull(committedAt, "committedAt");
        if (value.isBefore(createdAt)) {
            throw new IllegalArgumentException("Commit time must not precede batch creation");
        }
        status = TradeImportStatus.COMMITTED;
        this.committedAt = value;
    }
}
