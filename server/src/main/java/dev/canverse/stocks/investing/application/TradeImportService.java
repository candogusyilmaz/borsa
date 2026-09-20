package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.application.model.ParsedTradeImportFile;
import dev.canverse.stocks.investing.application.model.ParsedTradeImportRecord;
import dev.canverse.stocks.investing.domain.PositionProjection;
import dev.canverse.stocks.investing.domain.PositionState;
import dev.canverse.stocks.investing.domain.TradeEvent;
import dev.canverse.stocks.investing.domain.TradeImportBatch;
import dev.canverse.stocks.investing.domain.TradeImportFormat;
import dev.canverse.stocks.investing.domain.TradeImportIssue;
import dev.canverse.stocks.investing.domain.TradeImportIssueCode;
import dev.canverse.stocks.investing.domain.TradeImportNormalizationStatus;
import dev.canverse.stocks.investing.domain.TradeImportRow;
import dev.canverse.stocks.investing.domain.TradeImportStatus;
import dev.canverse.stocks.investing.domain.TradeReplayResult;
import dev.canverse.stocks.investing.domain.TradeSettlement;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.domain.WeightedAverageEconomicV1;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.InvestingReadRepository;
import dev.canverse.stocks.investing.infrastructure.PositionProjectionRepository;
import dev.canverse.stocks.investing.infrastructure.TradeImportBatchRepository;
import dev.canverse.stocks.investing.infrastructure.TradeImportCsvParser;
import dev.canverse.stocks.investing.infrastructure.TradeImportIssueRepository;
import dev.canverse.stocks.investing.infrastructure.TradeImportReadRepository;
import dev.canverse.stocks.investing.infrastructure.TradeImportReadRepository.CommittedActivity;
import dev.canverse.stocks.investing.infrastructure.TradeImportRowRepository;
import dev.canverse.stocks.investing.web.request.TradeImportCommitRequest;
import dev.canverse.stocks.investing.web.response.TradeImportBatchIssueResponse;
import dev.canverse.stocks.investing.web.response.TradeImportCommitResponse;
import dev.canverse.stocks.investing.web.response.TradeImportPositionImpactResponse;
import dev.canverse.stocks.investing.web.response.TradeImportPreviewResponse;
import dev.canverse.stocks.investing.web.response.TradeImportRowIssueResponse;
import dev.canverse.stocks.investing.web.response.TradeImportRowResponse;
import dev.canverse.stocks.investing.web.response.TradeImportSummaryResponse;
import dev.canverse.stocks.investing.web.response.TradeImportUploadResponse;
import dev.canverse.stocks.ledger.application.LedgerAccountAccess;
import dev.canverse.stocks.ledger.application.LedgerIdempotencyStore;
import dev.canverse.stocks.ledger.application.LedgerPolicyEvaluator;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.domain.Currency;
import dev.canverse.stocks.reference.domain.Instrument;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.infrastructure.CurrencyRepository;
import dev.canverse.stocks.reference.infrastructure.InstrumentRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TradeImportService {

    private static final String UPLOAD_SCOPE = "investing.trade_import.upload";
    private static final String CONTENT_LOCK_SCOPE = "investing.trade_import.content";
    private static final String COMMIT_SCOPE = "investing.trade_import.commit";
    private static final int MAX_SOURCE_VALUES_JSON_BYTES = 65_536;
    private static final Comparator<TradeImportRow> SOURCE_ORDER = Comparator.comparingInt(TradeImportRow::getSourceRecordNumber);
    private static final Comparator<TradeImportRow> ECONOMIC_ORDER = Comparator.comparing(TradeImportRow::getEffectiveAt)
            .thenComparing(TradeImportRow::getEconomicSequence).thenComparingInt(TradeImportRow::getSourceRecordNumber);
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);
    private static final Comparator<BatchIssue> BATCH_ISSUE_ORDER = Comparator.comparing(BatchIssue::code).thenComparing(BatchIssue::fieldName);

    private final TradeImportCsvParser csvParser;
    private final LedgerAccountAccess accountAccess;
    private final TradeImportBatchRepository batchRepository;
    private final TradeImportRowRepository rowRepository;
    private final TradeImportIssueRepository issueRepository;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final InstrumentRepository instrumentRepository;
    private final CurrencyRepository currencyRepository;
    private final PositionProjectionRepository positionProjectionRepository;
    private final InvestingReadRepository investingReadRepository;
    private final TradeImportReadRepository tradeImportReadRepository;
    private final FundedTradePostingService fundedTradePostingService;
    private final CanonicalFingerprint fingerprint;
    private final IdGenerator idGenerator;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate repeatableReadTransaction;

    public TradeImportService(TradeImportCsvParser csvParser, LedgerAccountAccess accountAccess, TradeImportBatchRepository batchRepository,
            TradeImportRowRepository rowRepository, TradeImportIssueRepository issueRepository, LedgerCommandLockRepository commandLockRepository,
            LedgerIdempotencyStore idempotencyStore, InstrumentRepository instrumentRepository, CurrencyRepository currencyRepository,
            PositionProjectionRepository positionProjectionRepository, InvestingReadRepository investingReadRepository,
            TradeImportReadRepository tradeImportReadRepository, FundedTradePostingService fundedTradePostingService, CanonicalFingerprint fingerprint,
            IdGenerator idGenerator, EntityManager entityManager, Clock clock, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.csvParser = csvParser;
        this.accountAccess = accountAccess;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.issueRepository = issueRepository;
        this.commandLockRepository = commandLockRepository;
        this.idempotencyStore = idempotencyStore;
        this.instrumentRepository = instrumentRepository;
        this.currencyRepository = currencyRepository;
        this.positionProjectionRepository = positionProjectionRepository;
        this.investingReadRepository = investingReadRepository;
        this.tradeImportReadRepository = tradeImportReadRepository;
        this.fundedTradePostingService = fundedTradePostingService;
        this.fingerprint = fingerprint;
        this.idGenerator = idGenerator;
        this.entityManager = entityManager;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.repeatableReadTransaction = new TransactionTemplate(transactionManager);
        this.repeatableReadTransaction.setReadOnly(true);
        this.repeatableReadTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public TradeImportUploadResponse upload(UUID ownerUserAccountId, UUID clientRequestId, UUID accountId, MultipartFile file) {
        var bytes = readBounded(file);
        var parsed = csvParser.parse(bytes, file.getOriginalFilename(), file.getContentType());
        return Objects.requireNonNull(writeTransaction.execute(status -> uploadParsed(ownerUserAccountId, clientRequestId, accountId, parsed)));
    }

    public TradeImportPreviewResponse preview(UUID ownerUserAccountId, UUID batchId) {
        return Objects.requireNonNull(repeatableReadTransaction.execute(status -> previewInTransaction(ownerUserAccountId, batchId)));
    }

    public TradeImportCommitResponse commit(UUID ownerUserAccountId, UUID batchId, TradeImportCommitRequest request) {
        return Objects.requireNonNull(writeTransaction.execute(status -> commitInTransaction(ownerUserAccountId, batchId, request)));
    }

    private TradeImportPreviewResponse previewInTransaction(UUID ownerUserAccountId, UUID batchId) {
        var batch = batchRepository.findOwned(ownerUserAccountId, batchId).orElseThrow(() -> new AppException(InvestingErrorCode.IMPORT_NOT_FOUND));
        var account = accountAccess.owned(ownerUserAccountId, batch.getFinancialAccountId());
        var rows = rowRepository.findAllByOwnerUserAccountIdAndImportBatchIdOrderBySourceRecordNumberAsc(ownerUserAccountId, batchId);
        var staticIssuesByRow = staticIssuesByRow(issueRepository.findAllByOwnerUserAccountIdAndImportBatchId(ownerUserAccountId, batchId));
        var committedActivities = tradeImportReadRepository.findActivitiesByImportRow(ownerUserAccountId, batchId);
        var totals = summarize(rows);
        if (batch.getStatus() == TradeImportStatus.COMMITTED) {
            return previewResponse(batch, account, rows, staticIssuesByRow, Map.of(), committedActivities, Map.of(), List.of(), totals, List.of(), null);
        }

        var simulation = simulate(ownerUserAccountId, batch, account, rows, staticIssuesByRow, totals, clock.instant(), Map.of());
        return previewResponse(batch, account, rows, staticIssuesByRow, simulation.rowIssues(), committedActivities, simulation.policyDecisions(),
                simulation.positionImpacts(), totals, simulation.batchIssues(), simulation);
    }

    private TradeImportPreviewResponse previewResponse(TradeImportBatch batch, FinancialAccount account, List<TradeImportRow> rows,
            Map<UUID, List<TradeImportIssue>> staticIssuesByRow, Map<UUID, Map<IssueKey, PreviewIssue>> dynamicIssuesByRow,
            Map<UUID, CommittedActivity> committedActivities, Map<UUID, PolicyDecision> policyDecisions,
            List<TradeImportPositionImpactResponse> positionImpacts, SummaryTotals totals, List<BatchIssue> batchIssues, Simulation simulation) {
        var responseBatchIssues = batchIssues.stream().distinct().sorted(BATCH_ISSUE_ORDER)
                .map(issue -> new TradeImportBatchIssueResponse(issue.code(), issue.fieldName(), issueDetail(issue.code()))).toList();
        var rowResponses = rows.stream().sorted(SOURCE_ORDER).map(row -> {
            var issues = rowIssues(staticIssuesByRow.getOrDefault(row.getId(), List.of()), dynamicIssuesByRow.getOrDefault(row.getId(), Map.of()));
            var committed = committedActivities.get(row.getId());
            var normalized = row.isValid();
            return new TradeImportRowResponse(row.getId(), row.getSourceRecordNumber(), row.getSourceValues(), normalized ? row.getSourceExternalId() : null,
                    normalized ? row.getSide() : null, normalized ? row.getInstrumentId() : null, normalized ? row.getCurrencyCode() : null,
                    normalized ? row.getEffectiveAt() : null, normalized ? row.getEconomicSequence() : null, normalized ? canonical(row.getQuantity()) : null,
                    normalized ? canonical(row.getUnitPrice()) : null, normalized ? canonical(row.getCommissionAmount()) : null,
                    normalized ? canonical(row.getGrossAmount()) : null, normalized ? canonical(row.getCashDelta()) : null,
                    normalized ? row.getRowFingerprint() : null, committed == null ? policyDecisions.get(row.getId()) : committed.policyDecision(),
                    committed == null ? null : committed.activityId(), issues);
        }).toList();
        var issueRowCount = (int) rowResponses.stream().filter(row -> !row.issues().isEmpty()).count();
        var duplicateRowCount = (int) rowResponses.stream().filter(row -> row.issues().stream().anyMatch(issue -> issue.code().startsWith("DUPLICATE_")))
                .count();
        var successful = simulation != null && simulation.successful();
        var summary = new TradeImportSummaryResponse(rows.size(), totals.normalizedRowCount(), issueRowCount, duplicateRowCount, totals.buyCount(),
                totals.sellCount(), account.getCurrencyCode(), canonical(totals.buyGross()), canonical(totals.sellGross()), canonical(totals.commissionTotal()),
                canonical(totals.cashDeltaTotal()), successful ? simulation.cashBefore().canonical() : null,
                successful ? simulation.cashAfter().canonical() : null, successful ? account.getVersion() : null,
                successful ? simulation.cashProjection().getVersion() : null);
        return new TradeImportPreviewResponse(batch.getId(), batch.getFinancialAccountId(), batch.getImportFormat(), batch.getStatus(),
                batch.getOriginalFileName(), batch.getMediaType(), batch.getByteSize(), batch.getContentSha256(), batch.getParsedRowCount(),
                batch.getCreatedAt(), batch.getCommittedAt(), successful, successful ? simulation.previewToken() : null, responseBatchIssues, summary,
                rowResponses, successful ? positionImpacts : List.of());
    }

    private TradeImportCommitResponse commitInTransaction(UUID ownerUserAccountId, UUID batchId, TradeImportCommitRequest request) {
        var requestHash = fingerprint.hash(fingerprint.values("batchId", batchId.toString(), "previewToken", request.previewToken()));
        commandLockRepository.lock(ownerUserAccountId, COMMIT_SCOPE, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, COMMIT_SCOPE, requestHash, TradeImportCommitReplaySnapshot.class);
        if (replay != null) {
            return replay.toResponse(batchId);
        }

        var batch = batchRepository.findOwnedForUpdate(ownerUserAccountId, batchId).orElseThrow(() -> new AppException(InvestingErrorCode.IMPORT_NOT_FOUND));
        if (batch.getStatus() == TradeImportStatus.COMMITTED) {
            throw new AppException(InvestingErrorCode.IMPORT_ALREADY_COMMITTED);
        }
        var rows = rowRepository.findAllByOwnerUserAccountIdAndImportBatchIdOrderBySourceRecordNumberAsc(ownerUserAccountId, batchId);
        var account = accountAccess.ownedForUpdate(ownerUserAccountId, batch.getFinancialAccountId());
        var cashProjection = accountAccess.projectionForUpdate(ownerUserAccountId, account.getId());
        var instrumentIds = rows.stream().filter(TradeImportRow::isValid).map(TradeImportRow::getInstrumentId).distinct().sorted(UUID_ORDER).toList();
        var lockedPositions = new LinkedHashMap<UUID, PositionProjection>();
        for (var instrumentId : instrumentIds) {
            positionProjectionRepository.findOwnedForUpdate(ownerUserAccountId, account.getId(), instrumentId)
                    .ifPresent(position -> lockedPositions.put(instrumentId, position));
        }
        for (var instrumentId : instrumentIds) {
            tradeImportReadRepository.lockVisibleInstrument(ownerUserAccountId, instrumentId);
        }

        var observedAt = clock.instant();
        var staticIssuesByRow = staticIssuesByRow(issueRepository.findAllByOwnerUserAccountIdAndImportBatchId(ownerUserAccountId, batchId));
        var activityIds = new LinkedHashMap<UUID, UUID>();
        for (var row : rows) {
            if (row.isValid()) {
                activityIds.put(row.getId(), idGenerator.next());
            }
        }
        var simulation = simulate(ownerUserAccountId, batch, account, rows, staticIssuesByRow, summarize(rows), observedAt, activityIds);
        if (!simulation.successful()) {
            throw new AppException(InvestingErrorCode.IMPORT_NOT_COMMITTABLE);
        }
        if (!simulation.previewToken().equals(request.previewToken())) {
            throw new AppException(InvestingErrorCode.IMPORT_PREVIEW_STALE);
        }

        for (var row : rows.stream().sorted(ECONOMIC_ORDER).toList()) {
            var activityId = activityIds.get(row.getId());
            fundedTradePostingService.post(activityId, ownerUserAccountId, batch.getId(), COMMIT_SCOPE, row.getSourceRecordNumber() - 1,
                    RecordingMode.HISTORICAL_FACT, row.getEffectiveAt(), observedAt, row.getEconomicSequence(), simulation.policyDecisions().get(row.getId()),
                    row.getId(), account, cashProjection.getCashPocket().getId(), row.getInstrumentId(), settlement(row));
            cashProjection.apply(amount(row.getCashDelta()), observedAt, activityId, observedAt);
        }

        var committedPositions = new ArrayList<dev.canverse.stocks.investing.web.response.TradeImportPositionCommitResponse>();
        for (var entry : simulation.positions().entrySet().stream().sorted(Map.Entry.comparingByKey(UUID_ORDER)).toList()) {
            var instrumentId = entry.getKey();
            var snapshot = entry.getValue();
            var position = lockedPositions.get(instrumentId);
            if (position == null) {
                position = PositionProjection.create(idGenerator.next(), ownerUserAccountId, account.getId(), instrumentId, account.getCurrencyCode(),
                        snapshot.after(), observedAt);
                positionProjectionRepository.save(position);
            } else {
                var firstEffectiveAt = rows.stream().filter(row -> row.getInstrumentId().equals(instrumentId)).map(TradeImportRow::getEffectiveAt)
                        .min(Instant::compareTo).orElseThrow();
                position.markStale(firstEffectiveAt, observedAt);
                position.beginRebuild(observedAt);
                position.completeRebuild(snapshot.after(), observedAt);
            }
            committedPositions.add(new dev.canverse.stocks.investing.web.response.TradeImportPositionCommitResponse(instrumentId, position.getVersion()));
        }

        batch.markCommitted(observedAt);
        entityManager.flush();
        var orderedActivityIds = rows.stream().sorted(SOURCE_ORDER).map(row -> activityIds.get(row.getId())).toList();
        committedPositions = committedPositions.stream().sorted(Comparator.comparing(position -> position.instrumentId().toString()))
                .map(position -> new dev.canverse.stocks.investing.web.response.TradeImportPositionCommitResponse(position.instrumentId(),
                        simulation.positions().get(position.instrumentId()).projection() == null ? positionProjectionRepository
                                .findOwned(ownerUserAccountId, account.getId(), position.instrumentId()).orElseThrow().getVersion()
                                : simulation.positions().get(position.instrumentId()).projection().getVersion()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var response = new TradeImportCommitResponse(batch.getId(), account.getId(), TradeImportStatus.COMMITTED, rows.size(), orderedActivityIds,
                cashProjection.balance().canonical(), cashProjection.getVersion(), committedPositions, observedAt);
        idempotencyStore.save(ownerUserAccountId, COMMIT_SCOPE, request.clientRequestId(), requestHash, "TRADE_IMPORT_BATCH", batch.getId(),
                TradeImportCommitReplaySnapshot.from(response), observedAt);
        entityManager.flush();
        return response;
    }

    private Simulation simulate(UUID ownerUserAccountId, TradeImportBatch batch, FinancialAccount account, List<TradeImportRow> rows,
            Map<UUID, List<TradeImportIssue>> staticIssuesByRow, SummaryTotals totals, Instant observedAt, Map<UUID, UUID> activityIds) {
        var rowIssues = new LinkedHashMap<UUID, Map<IssueKey, PreviewIssue>>();
        var batchIssues = new LinkedHashSet<BatchIssue>();
        if (account.isArchived()) {
            batchIssues.add(new BatchIssue("ACCOUNT_ARCHIVED", "account"));
        }
        if (account.getAccountKind() != AccountKind.BROKERAGE || account.getTrackingMode() != TrackingMode.FULL_LEDGER) {
            batchIssues.add(new BatchIssue("ACCOUNT_ACTION_NOT_SUPPORTED", "account"));
        }
        var validRows = rows.stream().filter(TradeImportRow::isValid).sorted(SOURCE_ORDER).toList();
        var allRowsStaticallyValid = validRows.size() == rows.size() && staticIssuesByRow.isEmpty();
        var cashProjection = accountAccess.projection(ownerUserAccountId, account.getId());

        for (var row : validRows) {
            if (row.getEffectiveAt().isAfter(observedAt)) {
                addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("EFFECTIVE_AT_FUTURE", "effective_at", null));
            }
            var committedDuplicate = tradeImportReadRepository.findCommittedImportedActivity(ownerUserAccountId, account.getId(), row.getRowFingerprint());
            var existingAtOrder = tradeImportReadRepository.findTradesAtEconomicOrder(ownerUserAccountId, account.getId(), row.getEffectiveAt(),
                    row.getEconomicSequence());
            if (committedDuplicate.isPresent()) {
                addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("DUPLICATE_EXISTING_TRADE", "row", committedDuplicate.get()));
            } else if (!existingAtOrder.isEmpty()) {
                if (existingAtOrder.size() == 1 && matches(row, existingAtOrder.getFirst())) {
                    addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("DUPLICATE_EXISTING_TRADE", "row", existingAtOrder.getFirst().activityId()));
                } else {
                    addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("EXISTING_ECONOMIC_ORDER_CONFLICT", "row", null));
                }
            }
        }

        if (totals.hasNumericOverflow()) {
            batchIssues.add(new BatchIssue("NUMERIC_OVERFLOW", "row"));
        }
        if (!accountEligibleForPreview(account) || !allRowsStaticallyValid || hasRowIssues(rowIssues) || !batchIssues.isEmpty()) {
            return Simulation.failed(rowIssues, batchIssues);
        }

        var instrumentIds = validRows.stream().map(TradeImportRow::getInstrumentId).distinct().sorted(UUID_ORDER).toList();
        var positionStates = new LinkedHashMap<UUID, PositionSnapshot>();
        for (var instrumentId : instrumentIds) {
            var instrument = instrumentRepository.findVisibleById(instrumentId, ownerUserAccountId).orElse(null);
            if (instrument == null || (instrument.getInstrumentType() != InstrumentType.EQUITY && instrument.getInstrumentType() != InstrumentType.ETF)) {
                var firstRow = validRows.stream().filter(row -> row.getInstrumentId().equals(instrumentId)).findFirst().orElseThrow();
                addPreviewIssue(rowIssues, firstRow.getId(), new PreviewIssue("INSTRUMENT_UNSUPPORTED", "instrument_id", null));
                continue;
            }
            var position = positionProjectionRepository.findOwned(ownerUserAccountId, account.getId(), instrumentId).orElse(null);
            var events = new ArrayList<>(investingReadRepository.findPositionEvents(ownerUserAccountId, account.getId(), instrumentId));
            var before = positionState(position);
            TradeReplayResult after = null;
            var positionRows = validRows.stream().filter(row -> row.getInstrumentId().equals(instrumentId)).sorted(ECONOMIC_ORDER).toList();
            for (var row : positionRows) {
                var activityId = activityIds.getOrDefault(row.getId(), previewActivityId(row.getId()));
                events.add(TradeEvent.trade(activityId, settlement(row), row.getEffectiveAt(), row.getEconomicSequence()));
                try {
                    after = WeightedAverageEconomicV1.replay(events);
                } catch (AppException exception) {
                    if (exception.getErrorCode() == InvestingErrorCode.INSUFFICIENT_POSITION_QUANTITY) {
                        addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("INSUFFICIENT_POSITION_QUANTITY", "quantity", null));
                    } else if (exception.getErrorCode() == InvestingErrorCode.DUPLICATE_ECONOMIC_ORDER) {
                        addPreviewIssue(rowIssues, row.getId(), new PreviewIssue("EXISTING_ECONOMIC_ORDER_CONFLICT", "economic_sequence", null));
                    } else {
                        batchIssues.add(new BatchIssue("NUMERIC_OVERFLOW", "row"));
                    }
                    break;
                }
            }
            if (after != null) {
                positionStates.put(instrumentId, new PositionSnapshot(instrument, position, before, after));
            }
        }

        var decisions = new LinkedHashMap<UUID, PolicyDecision>();
        var cashBefore = cashProjection.balance();
        var runningBalance = cashBefore;
        for (var row : validRows.stream().sorted(ECONOMIC_ORDER).toList()) {
            try {
                var evaluation = LedgerPolicyEvaluator.evaluate(account, runningBalance, amount(row.getCashDelta()), RecordingMode.HISTORICAL_FACT, false);
                decisions.put(row.getId(), evaluation.decision());
                runningBalance = runningBalance.add(amount(row.getCashDelta()));
            } catch (AppException | ArithmeticException | IllegalArgumentException exception) {
                batchIssues.add(new BatchIssue("NUMERIC_OVERFLOW", "row"));
                break;
            }
        }

        if (hasRowIssues(rowIssues) || !batchIssues.isEmpty() || positionStates.size() != instrumentIds.size() || decisions.size() != validRows.size()) {
            return Simulation.failed(rowIssues, batchIssues);
        }

        var cashAfter = runningBalance;
        var impacts = positionStates.values().stream().map(snapshot -> positionImpact(snapshot, account.getCurrencyCode())).toList();
        var token = previewToken(batch, account, cashProjection, rows, positionStates, decisions);
        return new Simulation(true, rowIssues, List.copyOf(batchIssues), decisions, cashProjection, cashBefore, cashAfter, impacts, positionStates, token);
    }

    private String previewToken(TradeImportBatch batch, FinancialAccount account, AccountBalanceProjection cashProjection, List<TradeImportRow> rows,
            Map<UUID, PositionSnapshot> positions, Map<UUID, PolicyDecision> decisions) {
        var values = new LinkedHashMap<String, Object>();
        values.put("batchId", batch.getId().toString());
        values.put("batchVersion", batch.getVersion());
        values.put("accountId", account.getId().toString());
        values.put("accountVersion", account.getVersion());
        values.put("cashProjectionId", cashProjection.getId().toString());
        values.put("cashProjectionVersion", cashProjection.getVersion());
        values.put("cashBalance", cashProjection.balance().canonical());
        var index = 0;
        for (var entry : positions.entrySet().stream().sorted(Map.Entry.comparingByKey(UUID_ORDER)).toList()) {
            var instrumentId = entry.getKey();
            var snapshot = entry.getValue();
            values.put("position.%d.instrumentId".formatted(index), instrumentId.toString());
            values.put("position.%d.instrumentVersion".formatted(index), snapshot.instrument().getVersion());
            values.put("position.%d.projectionId".formatted(index), snapshot.projection() == null ? null : snapshot.projection().getId().toString());
            values.put("position.%d.projectionVersion".formatted(index), snapshot.projection() == null ? 0L : snapshot.projection().getVersion());
            values.put("position.%d.quantityBefore".formatted(index), snapshot.before().quantity().canonical());
            values.put("position.%d.quantityAfter".formatted(index), snapshot.after().state().quantity().canonical());
            values.put("position.%d.basisBefore".formatted(index), snapshot.before().remainingBasis().canonical());
            values.put("position.%d.basisAfter".formatted(index), snapshot.after().state().remainingBasis().canonical());
            values.put("position.%d.realizedPnlBefore".formatted(index), snapshot.before().realizedEconomicPnl().canonical());
            values.put("position.%d.realizedPnlAfter".formatted(index), snapshot.after().state().realizedEconomicPnl().canonical());
            index++;
        }
        index = 0;
        for (var row : rows.stream().sorted(SOURCE_ORDER).toList()) {
            values.put("row.%d.fingerprint".formatted(index), row.getRowFingerprint());
            values.put("row.%d.policyDecision".formatted(index), decisions.get(row.getId()).name());
            index++;
        }
        return fingerprint.hash(values);
    }

    private static TradeImportPositionImpactResponse positionImpact(PositionSnapshot snapshot, String currencyCode) {
        var projection = snapshot.projection();
        var before = snapshot.before();
        var after = snapshot.after().state();
        return new TradeImportPositionImpactResponse(snapshot.instrument().getId(), snapshot.instrument().getSymbol(), currencyCode,
                projection == null ? 0 : projection.getVersion(), before.quantity().canonical(), after.quantity().canonical(),
                before.remainingBasis().canonical(), after.remainingBasis().canonical(), before.realizedEconomicPnl().canonical(),
                after.realizedEconomicPnl().canonical());
    }

    private static PositionState positionState(PositionProjection projection) {
        return projection == null ? new PositionState(FinancialAmount.zero(), FinancialAmount.zero(), FinancialAmount.zero())
                : new PositionState(projection.quantity(), projection.remainingBasis(), projection.realizedEconomicPnl());
    }

    private static boolean matches(TradeImportRow row, TradeImportReadRepository.ExistingTrade existing) {
        return row.getInstrumentId().equals(existing.instrumentId()) && row.getSide() == existing.side() && row.getCurrencyCode().equals(existing.currency()) &&
                amount(row.getQuantity()).equals(existing.quantity()) && amount(row.getUnitPrice()).equals(existing.unitPrice()) &&
                amount(row.getGrossAmount()).equals(existing.grossAmount()) && amount(row.getCommissionAmount()).equals(existing.commissionAmount());
    }

    private static TradeSettlement settlement(TradeImportRow row) {
        return new TradeSettlement(row.getSide(), amount(row.getQuantity()), amount(row.getUnitPrice()), amount(row.getCommissionAmount()),
                amount(row.getGrossAmount()), amount(row.getCashDelta()));
    }

    private static FinancialAmount amount(BigDecimal value) {
        return FinancialAmount.of(value);
    }

    private static UUID previewActivityId(UUID rowId) {
        return UUID.nameUUIDFromBytes(("trade-import-preview:" + rowId).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean accountEligibleForPreview(FinancialAccount account) {
        return !account.isArchived() && account.getAccountKind() == AccountKind.BROKERAGE && account.getTrackingMode() == TrackingMode.FULL_LEDGER;
    }

    private static Map<UUID, List<TradeImportIssue>> staticIssuesByRow(List<TradeImportIssue> issues) {
        var grouped = new LinkedHashMap<UUID, List<TradeImportIssue>>();
        for (var issue : issues) {
            grouped.computeIfAbsent(issue.getImportRowId(), ignored -> new ArrayList<>()).add(issue);
        }
        grouped.replaceAll((rowId, rowIssues) -> rowIssues.stream()
                .sorted(Comparator.comparing((TradeImportIssue issue) -> issue.getIssueCode().name()).thenComparing(TradeImportIssue::getFieldName)).toList());
        return grouped;
    }

    private static List<TradeImportRowIssueResponse> rowIssues(List<TradeImportIssue> staticIssues, Map<IssueKey, PreviewIssue> dynamicIssues) {
        var result = new LinkedHashMap<IssueKey, TradeImportRowIssueResponse>();
        for (var issue : staticIssues) {
            var code = issue.getIssueCode().name();
            result.put(new IssueKey(code, issue.getFieldName()), new TradeImportRowIssueResponse(code, issue.getFieldName(), issueDetail(code), null));
        }
        dynamicIssues.values().forEach(issue -> result.put(new IssueKey(issue.code(), issue.fieldName()),
                new TradeImportRowIssueResponse(issue.code(), issue.fieldName(), issueDetail(issue.code()), issue.relatedActivityId())));
        return result.values().stream().sorted(Comparator.comparing(TradeImportRowIssueResponse::code).thenComparing(TradeImportRowIssueResponse::field))
                .toList();
    }

    private static void addPreviewIssue(Map<UUID, Map<IssueKey, PreviewIssue>> issues, UUID rowId, PreviewIssue issue) {
        issues.computeIfAbsent(rowId, ignored -> new LinkedHashMap<>()).putIfAbsent(new IssueKey(issue.code(), issue.fieldName()), issue);
    }

    private static boolean hasRowIssues(Map<UUID, Map<IssueKey, PreviewIssue>> rowIssues) {
        return rowIssues.values().stream().anyMatch(issues -> !issues.isEmpty());
    }

    private static SummaryTotals summarize(List<TradeImportRow> rows) {
        var normalizedRows = 0;
        var buyCount = 0;
        var sellCount = 0;
        var buyGross = BigDecimal.ZERO;
        var sellGross = BigDecimal.ZERO;
        var commissionTotal = BigDecimal.ZERO;
        var cashDeltaTotal = BigDecimal.ZERO;
        for (var row : rows) {
            if (!row.isValid()) {
                continue;
            }
            normalizedRows++;
            if (row.getSide() == TradeSide.BUY) {
                buyCount++;
                buyGross = buyGross.add(row.getGrossAmount());
            } else {
                sellCount++;
                sellGross = sellGross.add(row.getGrossAmount());
            }
            commissionTotal = commissionTotal.add(row.getCommissionAmount());
            cashDeltaTotal = cashDeltaTotal.add(row.getCashDelta());
        }
        return new SummaryTotals(normalizedRows, buyCount, sellCount, buyGross, sellGross, commissionTotal, cashDeltaTotal, !fitsFinancialAmount(buyGross) ||
                !fitsFinancialAmount(sellGross) || !fitsFinancialAmount(commissionTotal) || !fitsFinancialAmount(cashDeltaTotal));
    }

    private static boolean fitsFinancialAmount(BigDecimal value) {
        try {
            FinancialAmount.of(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String issueDetail(String code) {
        return switch (code) {
            case "RECORD_SHAPE_INVALID" -> "The CSV record must contain exactly nine fields.";
            case "EXTERNAL_ID_INVALID" -> "The external identifier must contain 1 to 200 characters.";
            case "SIDE_INVALID" -> "The side must be BUY or SELL.";
            case "INSTRUMENT_ID_INVALID" -> "The instrument identifier must be a canonical UUID.";
            case "INSTRUMENT_UNSUPPORTED" -> "The instrument is not available for this owner or is not an equity or ETF.";
            case "CURRENCY_INVALID" -> "The currency code is not available.";
            case "CURRENCY_MISMATCH" -> "The row currency must match the account and instrument currencies.";
            case "EFFECTIVE_AT_INVALID" -> "The effective time must be a UTC ISO-8601 instant with microsecond precision.";
            case "ECONOMIC_SEQUENCE_INVALID" -> "The economic sequence must be a non-negative base-10 integer.";
            case "QUANTITY_INVALID" -> "The quantity must be a positive exact decimal within supported precision.";
            case "UNIT_PRICE_INVALID" -> "The unit price must be a positive exact decimal within supported precision.";
            case "COMMISSION_INVALID" -> "The commission must be a non-negative exact decimal within supported precision.";
            case "SETTLED_PRECISION_INVALID" -> "The settled trade amounts exceed the supported precision.";
            case "SELL_PROCEEDS_NOT_POSITIVE" -> "Sell proceeds after commission must be positive.";
            case "DUPLICATE_EXTERNAL_ID" -> "The external identifier is repeated in this file.";
            case "DUPLICATE_ROW_IN_FILE" -> "The same normalized trade appears earlier in this file.";
            case "ECONOMIC_ORDER_CONFLICT_IN_FILE" -> "Another row in this file uses the same effective time and economic sequence.";
            case "EFFECTIVE_AT_FUTURE" -> "The effective time is later than the current preview time.";
            case "DUPLICATE_EXISTING_TRADE" -> "An equivalent trade already exists in this account.";
            case "EXISTING_ECONOMIC_ORDER_CONFLICT" -> "Another account trade uses this effective time and economic sequence.";
            case "INSUFFICIENT_POSITION_QUANTITY" -> "The batch would sell more units than are held at this point in history.";
            case "ACCOUNT_ARCHIVED" -> "The brokerage account is archived.";
            case "ACCOUNT_ACTION_NOT_SUPPORTED" -> "The account is not an active full-ledger brokerage account.";
            case "NUMERIC_OVERFLOW" -> "The complete batch calculation exceeds supported numeric precision.";
            default -> "The row or batch cannot be processed in its current state.";
        };
    }

    private record IssueKey(String code, String fieldName) {}

    private record PreviewIssue(String code, String fieldName, UUID relatedActivityId) {}

    private record BatchIssue(String code, String fieldName) {}

    private record SummaryTotals(int normalizedRowCount, int buyCount, int sellCount, BigDecimal buyGross, BigDecimal sellGross, BigDecimal commissionTotal,
            BigDecimal cashDeltaTotal, boolean hasNumericOverflow) {}

    private record PositionSnapshot(Instrument instrument, PositionProjection projection, PositionState before, TradeReplayResult after) {}

    private record Simulation(boolean successful, Map<UUID, Map<IssueKey, PreviewIssue>> rowIssues, List<BatchIssue> batchIssues,
            Map<UUID, PolicyDecision> policyDecisions, AccountBalanceProjection cashProjection, FinancialAmount cashBefore, FinancialAmount cashAfter,
            List<TradeImportPositionImpactResponse> positionImpacts, Map<UUID, PositionSnapshot> positions, String previewToken) {

        static Simulation failed(Map<UUID, Map<IssueKey, PreviewIssue>> rowIssues, Set<BatchIssue> batchIssues) {
            return new Simulation(false, rowIssues, List.copyOf(batchIssues), Map.of(), null, null, null, List.of(), Map.of(), null);
        }
    }

    private TradeImportUploadResponse uploadParsed(UUID ownerUserAccountId, UUID clientRequestId, UUID accountId, ParsedTradeImportFile parsed) {
        var requestHash = fingerprint.hash(fingerprint.values("accountId", accountId.toString(), "importFormat", TradeImportFormat.FUNDED_TRADE_CSV_V1.name(),
                "contentSha256", parsed.contentSha256()));
        commandLockRepository.lock(ownerUserAccountId, UPLOAD_SCOPE, clientRequestId);
        var replay = idempotencyStore.replay(clientRequestId, ownerUserAccountId, UPLOAD_SCOPE, requestHash, TradeImportUploadResponse.class);
        if (replay != null) {
            return replay;
        }
        commandLockRepository.lock(ownerUserAccountId, CONTENT_LOCK_SCOPE, contentLockId(parsed.contentSha256()));

        var account = accountAccess.owned(ownerUserAccountId, accountId);
        requireUploadEligibleAccount(account);
        var existing = batchRepository.findFirstByOwnerUserAccountIdAndFinancialAccountIdAndImportFormatAndContentSha256(ownerUserAccountId, accountId,
                TradeImportFormat.FUNDED_TRADE_CSV_V1, parsed.contentSha256()).orElse(null);
        var observedAt = clock.instant();
        if (existing != null) {
            var response = uploadResponse(existing, true);
            idempotencyStore.save(ownerUserAccountId, UPLOAD_SCOPE, clientRequestId, requestHash, "TRADE_IMPORT_BATCH", existing.getId(), response, observedAt);
            return response;
        }

        var batch = TradeImportBatch.parsed(idGenerator.next(), ownerUserAccountId, accountId, parsed.originalFileName(), parsed.mediaType(), parsed.byteSize(),
                parsed.contentSha256(), parsed.records().size(), observedAt);
        batchRepository.save(batch);
        var normalizedRows = normalizeRows(ownerUserAccountId, batch.getId(), account, parsed.records(), observedAt);
        rowRepository.saveAll(normalizedRows.rows());
        issueRepository.saveAll(normalizedRows.issues());
        entityManager.flush();

        var response = uploadResponse(batch, false);
        idempotencyStore.save(ownerUserAccountId, UPLOAD_SCOPE, clientRequestId, requestHash, "TRADE_IMPORT_BATCH", batch.getId(), response, observedAt);
        entityManager.flush();
        return response;
    }

    private NormalizedRows normalizeRows(UUID ownerUserAccountId, UUID batchId, FinancialAccount account, List<ParsedTradeImportRecord> parsedRows,
            Instant createdAt) {
        var accountCurrency = currencyRepository.findById(account.getCurrencyCode())
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED));
        var instrumentCache = new HashMap<UUID, Optional<Instrument>>();
        var currencyCache = new HashMap<String, Optional<Currency>>();
        var seenExternalIds = new HashSet<String>();
        var seenFingerprints = new HashSet<String>();
        var seenEconomicOrders = new HashSet<EconomicOrder>();
        var rows = new ArrayList<TradeImportRow>(parsedRows.size());
        var issues = new ArrayList<TradeImportIssue>();

        for (var parsedRow : parsedRows) {
            requireSourceValuesSize(parsedRow.values());
            var rowIssues = new LinkedHashSet<NormalizationIssue>();
            if (parsedRow.values().size() != 9) {
                rowIssues.add(new NormalizationIssue(TradeImportIssueCode.RECORD_SHAPE_INVALID, "row"));
                rows.add(invalidRow(ownerUserAccountId, batchId, parsedRow, null, rowIssues, createdAt, issues));
                continue;
            }

            var values = parsedRow.values();
            var externalId = parseExternalId(values.get(0), rowIssues);
            if (externalId != null && !seenExternalIds.add(externalId)) {
                rowIssues.add(new NormalizationIssue(TradeImportIssueCode.DUPLICATE_EXTERNAL_ID, "external_id"));
            }
            var side = parseSide(values.get(1), rowIssues);
            var instrument = parseInstrument(ownerUserAccountId, values.get(2), instrumentCache, rowIssues);
            var currency = parseCurrency(values.get(3), currencyCache, rowIssues);
            var currencyMatches = currency != null && currency.getCode().equals(account.getCurrencyCode()) && instrument != null &&
                    currency.getCode().equals(instrument.getQuotationCurrencyCode());
            if (currency != null && (!currency.getCode().equals(account.getCurrencyCode()) ||
                    (instrument != null && !currency.getCode().equals(instrument.getQuotationCurrencyCode())))) {
                rowIssues.add(new NormalizationIssue(TradeImportIssueCode.CURRENCY_MISMATCH, "currency"));
            }
            var effectiveAt = parseEffectiveAt(values.get(4), rowIssues);
            var economicSequence = parseEconomicSequence(values.get(5), rowIssues);
            var quantity = parseAmount(values.get(6), TradeImportIssueCode.QUANTITY_INVALID, true, rowIssues);
            var unitPrice = parseAmount(values.get(7), TradeImportIssueCode.UNIT_PRICE_INVALID, true, rowIssues);
            var commission = parseAmount(values.get(8), TradeImportIssueCode.COMMISSION_INVALID, false, rowIssues);

            var settlement = settle(side, quantity, unitPrice, commission, accountCurrency, rowIssues);
            var economicallyComplete = externalId != null && side != null && instrument != null && currencyMatches && effectiveAt != null &&
                    economicSequence != null && quantity != null && unitPrice != null && commission != null && settlement != null;
            var rowFingerprint = economicallyComplete ? rowFingerprint(account.getId(), instrument.getId(), side, currency.getCode(), effectiveAt,
                    economicSequence, quantity, unitPrice, commission, settlement.grossAmount()) : null;
            if (economicallyComplete) {
                if (!seenEconomicOrders.add(new EconomicOrder(effectiveAt, economicSequence))) {
                    rowIssues.add(new NormalizationIssue(TradeImportIssueCode.ECONOMIC_ORDER_CONFLICT_IN_FILE, "row"));
                }
                if (!seenFingerprints.add(rowFingerprint)) {
                    rowIssues.add(new NormalizationIssue(TradeImportIssueCode.DUPLICATE_ROW_IN_FILE, "row"));
                }
            }

            if (rowIssues.isEmpty() && economicallyComplete) {
                rows.add(TradeImportRow.create(idGenerator.next(), ownerUserAccountId, batchId, parsedRow.sourceRecordNumber(), externalId, values,
                        TradeImportNormalizationStatus.VALID, side, instrument.getId(), currency.getCode(), effectiveAt, economicSequence, quantity, unitPrice,
                        commission, settlement.grossAmount(), settlement.cashDelta(), rowFingerprint, createdAt));
            } else {
                rows.add(TradeImportRow.create(idGenerator.next(), ownerUserAccountId, batchId, parsedRow.sourceRecordNumber(), externalId, values,
                        TradeImportNormalizationStatus.INVALID, null, null, null, null, null, null, null, null, null, null, null, createdAt));
                persistIssues(ownerUserAccountId, batchId, rows.getLast().getId(), rowIssues, createdAt, issues);
            }
        }
        return new NormalizedRows(List.copyOf(rows), List.copyOf(issues));
    }

    private TradeImportRow invalidRow(UUID ownerUserAccountId, UUID batchId, ParsedTradeImportRecord parsedRow, String externalId,
            Set<NormalizationIssue> rowIssues, Instant createdAt, List<TradeImportIssue> persistedIssues) {
        var row = TradeImportRow.create(idGenerator.next(), ownerUserAccountId, batchId, parsedRow.sourceRecordNumber(), externalId, parsedRow.values(),
                TradeImportNormalizationStatus.INVALID, null, null, null, null, null, null, null, null, null, null, null, createdAt);
        persistIssues(ownerUserAccountId, batchId, row.getId(), rowIssues, createdAt, persistedIssues);
        return row;
    }

    private void persistIssues(UUID ownerUserAccountId, UUID batchId, UUID rowId, Set<NormalizationIssue> rowIssues, Instant createdAt,
            List<TradeImportIssue> persistedIssues) {
        for (var issue : rowIssues) {
            persistedIssues.add(TradeImportIssue.create(idGenerator.next(), ownerUserAccountId, batchId, rowId, issue.code(), issue.fieldName(), createdAt));
        }
    }

    private String parseExternalId(String value, Set<NormalizationIssue> issues) {
        if (value.isBlank() || value.codePointCount(0, value.length()) > 200) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.EXTERNAL_ID_INVALID, "external_id"));
            return null;
        }
        return value;
    }

    private static TradeSide parseSide(String value, Set<NormalizationIssue> issues) {
        try {
            return TradeSide.valueOf(value);
        } catch (IllegalArgumentException exception) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.SIDE_INVALID, "side"));
            return null;
        }
    }

    private Instrument parseInstrument(UUID ownerUserAccountId, String value, Map<UUID, Optional<Instrument>> instrumentCache, Set<NormalizationIssue> issues) {
        UUID instrumentId;
        try {
            instrumentId = UUID.fromString(value);
            if (!instrumentId.toString().equals(value)) {
                throw new IllegalArgumentException("UUID must use canonical lowercase text");
            }
        } catch (IllegalArgumentException exception) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.INSTRUMENT_ID_INVALID, "instrument_id"));
            return null;
        }
        var instrument = instrumentCache.computeIfAbsent(instrumentId, id -> instrumentRepository.findVisibleById(id, ownerUserAccountId)).orElse(null);
        if (instrument == null || (instrument.getInstrumentType() != InstrumentType.EQUITY && instrument.getInstrumentType() != InstrumentType.ETF)) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.INSTRUMENT_UNSUPPORTED, "instrument_id"));
            return null;
        }
        return instrument;
    }

    private Currency parseCurrency(String value, Map<String, Optional<Currency>> currencyCache, Set<NormalizationIssue> issues) {
        if (value.isBlank()) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.CURRENCY_INVALID, "currency"));
            return null;
        }
        var currency = currencyCache.computeIfAbsent(value, currencyRepository::findById).orElse(null);
        if (currency == null) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.CURRENCY_INVALID, "currency"));
        }
        return currency;
    }

    private static Instant parseEffectiveAt(String value, Set<NormalizationIssue> issues) {
        try {
            if (!value.endsWith("Z")) {
                throw new DateTimeParseException("UTC instant must end with Z", value, value.length());
            }
            var effectiveAt = Instant.parse(value);
            if (effectiveAt.getNano() % 1_000 != 0) {
                throw new DateTimeParseException("Instant must have microsecond precision", value, value.length());
            }
            return effectiveAt;
        } catch (DateTimeParseException exception) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.EFFECTIVE_AT_INVALID, "effective_at"));
            return null;
        }
    }

    private static Long parseEconomicSequence(String value, Set<NormalizationIssue> issues) {
        try {
            if (!value.matches("[0-9]+")) {
                throw new NumberFormatException("Expected non-negative base-10 digits");
            }
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.ECONOMIC_SEQUENCE_INVALID, "economic_sequence"));
            return null;
        }
    }

    private static FinancialAmount parseAmount(String value, TradeImportIssueCode issueCode, boolean positive, Set<NormalizationIssue> issues) {
        try {
            var amount = FinancialAmount.parse(value);
            if (positive ? amount.isPositive() : !amount.isNegative()) {
                return amount;
            }
        } catch (IllegalArgumentException exception) {
            // The field-specific issue intentionally hides parser details.
        }
        issues.add(new NormalizationIssue(issueCode, fieldName(issueCode)));
        return null;
    }

    private static dev.canverse.stocks.investing.domain.TradeSettlement settle(TradeSide side, FinancialAmount quantity, FinancialAmount unitPrice,
            FinancialAmount commission, Currency currency, Set<NormalizationIssue> issues) {
        if (side == null || quantity == null || unitPrice == null || commission == null) {
            return null;
        }
        try {
            return dev.canverse.stocks.investing.domain.TradeSettlement.calculate(side, quantity, unitPrice, commission, currency.getMinorUnit());
        } catch (AppException exception) {
            var code = exception.getErrorCode() == InvestingErrorCode.TRADE_PROCEEDS_NOT_POSITIVE ? TradeImportIssueCode.SELL_PROCEEDS_NOT_POSITIVE
                    : TradeImportIssueCode.SETTLED_PRECISION_INVALID;
            issues.add(new NormalizationIssue(code, "row"));
            return null;
        } catch (IllegalArgumentException exception) {
            issues.add(new NormalizationIssue(TradeImportIssueCode.SETTLED_PRECISION_INVALID, "row"));
            return null;
        }
    }

    private String rowFingerprint(UUID accountId, UUID instrumentId, TradeSide side, String currency, Instant effectiveAt, long economicSequence,
            FinancialAmount quantity, FinancialAmount unitPrice, FinancialAmount commission, FinancialAmount gross) {
        return fingerprint.hash(fingerprint.values("importFormat", TradeImportFormat.FUNDED_TRADE_CSV_V1.name(), "accountId", accountId.toString(),
                "instrumentId", instrumentId.toString(), "side", side.name(), "currency", currency, "effectiveAt", effectiveAt.toString(), "economicSequence",
                economicSequence, "quantity", quantity.canonical(), "unitPrice", unitPrice.canonical(), "commissionAmount", commission.canonical(),
                "grossAmount", gross.canonical()));
    }

    private void requireSourceValuesSize(List<String> values) {
        try {
            var compactSize = objectMapper.writeValueAsBytes(values).length;
            var postgresSeparatorSize = Math.max(0, values.size() - 1);
            if (compactSize + postgresSeparatorSize > MAX_SOURCE_VALUES_JSON_BYTES) {
                throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to encode parsed import source values", exception);
        }
    }

    private static byte[] readBounded(MultipartFile file) {
        Objects.requireNonNull(file, "file");
        var declaredSize = file.getSize();
        if (declaredSize > TradeImportCsvParser.MAX_FILE_BYTES) {
            throw new AppException(dev.canverse.stocks.platform.error.CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        if (declaredSize < 0 || declaredSize > Integer.MAX_VALUE) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
        }
        try (InputStream inputStream = file.getInputStream()) {
            var bytes = inputStream.readNBytes(TradeImportCsvParser.MAX_FILE_BYTES + 1);
            if (bytes.length > TradeImportCsvParser.MAX_FILE_BYTES) {
                throw new AppException(dev.canverse.stocks.platform.error.CommonErrorCode.PAYLOAD_TOO_LARGE);
            }
            if (bytes.length != declaredSize) {
                throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID);
            }
            return bytes;
        } catch (IOException exception) {
            throw new AppException(InvestingErrorCode.IMPORT_FILE_FORMAT_INVALID, exception);
        }
    }

    private static void requireUploadEligibleAccount(FinancialAccount account) {
        if (account.isArchived()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ARCHIVED);
        }
        if (account.getAccountKind() != AccountKind.BROKERAGE || account.getTrackingMode() != TrackingMode.FULL_LEDGER) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED);
        }
    }

    private static String fieldName(TradeImportIssueCode issueCode) {
        return switch (issueCode) {
            case QUANTITY_INVALID -> "quantity";
            case UNIT_PRICE_INVALID -> "unit_price";
            case COMMISSION_INVALID -> "commission_amount";
            default -> "row";
        };
    }

    private static UUID contentLockId(String contentSha256) {
        var bytes = java.util.HexFormat.of().parseHex(contentSha256.substring(0, 32));
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static TradeImportUploadResponse uploadResponse(TradeImportBatch batch, boolean duplicateContent) {
        return new TradeImportUploadResponse(batch.getId(), batch.getFinancialAccountId(), batch.getImportFormat(), batch.getStatus(), duplicateContent,
                batch.getCreatedAt(), "/api/v1/imports/%s/preview".formatted(batch.getId()));
    }

    private static String canonical(BigDecimal value) {
        var normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return normalized.toPlainString();
    }

    private static String duplicateKey(UUID ownerUserAccountId, UUID accountId, String format, String contentSha256) {
        return "%s:%s:%s:%s".formatted(ownerUserAccountId, accountId, format, contentSha256);
    }

    private record NormalizationIssue(TradeImportIssueCode code, String fieldName) {}

    private record NormalizedRows(List<TradeImportRow> rows, List<TradeImportIssue> issues) {}

    private record EconomicOrder(Instant effectiveAt, long economicSequence) {}
}
