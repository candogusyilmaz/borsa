package dev.canverse.stocks.investing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade_import_issue", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeImportIssue {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID importBatchId;
    private UUID importRowId;

    @Enumerated(EnumType.STRING)
    private TradeImportIssueCode issueCode;

    private String fieldName;
    private Instant createdAt;

    public static TradeImportIssue create(UUID id, UUID ownerUserAccountId, UUID importBatchId, UUID importRowId, TradeImportIssueCode issueCode,
            String fieldName, Instant createdAt) {
        var issue = new TradeImportIssue();
        issue.id = Objects.requireNonNull(id, "id");
        issue.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        issue.importBatchId = Objects.requireNonNull(importBatchId, "importBatchId");
        issue.importRowId = Objects.requireNonNull(importRowId, "importRowId");
        issue.issueCode = Objects.requireNonNull(issueCode, "issueCode");
        issue.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        issue.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return issue;
    }
}
