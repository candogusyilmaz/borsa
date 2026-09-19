package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.application.model.PositionReadModel;
import dev.canverse.stocks.investing.application.model.TradeReadModel;
import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.TradeEvent;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.web.response.PositionResponse;
import dev.canverse.stocks.investing.web.response.SecurityPostingResponse;
import dev.canverse.stocks.investing.web.response.TradeSummaryResponse;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.PostingRole;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.web.response.PostingResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.web.SliceResponse;
import dev.canverse.stocks.reference.domain.InstrumentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Owner-scoped JDBC read models and replay inputs for the investing capability. */
@Repository
@RequiredArgsConstructor
public class InvestingReadRepository {

    private final JdbcClient jdbcClient;

    public Optional<TradeReadModel> findTrade(UUID ownerUserAccountId, UUID activityId) {
        var row = tradeSql("a.id = :activityId").param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                .param("activityId", Objects.requireNonNull(activityId, "activityId")).query(this::mapTradeRow).optional();
        return row.map(value -> toTradeReadModels(ownerUserAccountId, List.of(value)).getFirst());
    }

    public SliceResponse<TradeSummaryResponse> findTrades(UUID ownerUserAccountId, UUID accountId, UUID instrumentId, Pageable pageable) {
        var pageSize = Objects.requireNonNull(pageable, "pageable").getPageSize();
        var predicate = new StringBuilder("a.owner_user_account_id = :ownerUserAccountId");
        if (accountId != null) {
            predicate.append(" AND s.financial_account_id = :accountId");
        }
        if (instrumentId != null) {
            predicate.append(" AND s.instrument_id = :instrumentId");
        }
        var statement = tradeSql(predicate.toString() + tradeOrderBy(pageable) + " LIMIT :fetchLimit OFFSET :offset")
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId")).param("fetchLimit", pageSize + 1)
                .param("offset", pageable.getOffset());
        if (accountId != null) {
            statement = statement.param("accountId", accountId);
        }
        if (instrumentId != null) {
            statement = statement.param("instrumentId", instrumentId);
        }
        var rows = statement.query(this::mapTradeRow).list();
        var hasNext = rows.size() > pageSize;
        var pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        var items = toTradeReadModels(ownerUserAccountId, pageRows).stream().map(TradeSummaryResponse::from).toList();
        return new SliceResponse<>(items, pageable.getPageNumber(), pageSize, hasNext);
    }

    public List<TradeEvent> findPositionEvents(UUID ownerUserAccountId, UUID accountId, UUID instrumentId) {
        return jdbcClient.sql("""
                WITH fee_totals AS (
                    SELECT owner_user_account_id, activity_id, -SUM(amount) AS commission_amount
                    FROM ledger.money_posting
                    WHERE owner_user_account_id = :ownerUserAccountId AND posting_role = 'FEE'
                    GROUP BY owner_user_account_id, activity_id
                )
                SELECT s.activity_id, a.activity_type, s.quantity_delta, s.gross_amount,
                       COALESCE(f.commission_amount, 0) AS commission_amount, s.effective_at,
                       s.economic_sequence, original.id AS reverses_activity_id
                FROM ledger.security_posting s
                JOIN ledger.activity a ON a.owner_user_account_id = s.owner_user_account_id
                    AND a.id = s.activity_id
                LEFT JOIN ledger.security_posting original_posting
                    ON original_posting.owner_user_account_id = s.owner_user_account_id
                   AND original_posting.id = s.reverses_security_posting_id
                LEFT JOIN fee_totals f ON f.owner_user_account_id = s.owner_user_account_id
                    AND f.activity_id = s.activity_id
                LEFT JOIN ledger.activity original
                    ON original.owner_user_account_id = original_posting.owner_user_account_id
                   AND original.id = original_posting.activity_id
                WHERE s.owner_user_account_id = :ownerUserAccountId
                  AND s.financial_account_id = :accountId
                  AND s.instrument_id = :instrumentId
                ORDER BY s.effective_at, s.economic_sequence, s.activity_id
                """).param("ownerUserAccountId", ownerUserAccountId).param("accountId", accountId).param("instrumentId", instrumentId)
                .query((resultSet, rowNumber) -> mapTradeEvent(resultSet)).list();
    }

    public SliceResponse<PositionResponse> findOpenPositions(UUID ownerUserAccountId, UUID accountId, Pageable pageable) {
        var pageSize = Objects.requireNonNull(pageable, "pageable").getPageSize();
        var predicate = new StringBuilder("p.owner_user_account_id = :ownerUserAccountId AND p.current_quantity > 0");
        if (accountId != null) {
            predicate.append(" AND p.financial_account_id = :accountId");
        }
        var statement = jdbcClient.sql(positionSql(predicate.toString() + positionOrderBy(pageable) + " LIMIT :fetchLimit OFFSET :offset"))
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId")).param("fetchLimit", pageSize + 1)
                .param("offset", pageable.getOffset());
        if (accountId != null) {
            statement = statement.param("accountId", accountId);
        }
        var rows = statement.query(this::mapPositionReadModel).list();
        var hasNext = rows.size() > pageSize;
        var items = (hasNext ? rows.subList(0, pageSize) : rows).stream().map(PositionResponse::from).toList();
        return new SliceResponse<>(items, pageable.getPageNumber(), pageSize, hasNext);
    }

    public Optional<PositionReadModel> findPosition(UUID ownerUserAccountId, UUID accountId, UUID instrumentId) {
        return jdbcClient
                .sql(positionSql(
                        "p.owner_user_account_id = :ownerUserAccountId AND p.financial_account_id = :accountId" + " AND p.instrument_id = :instrumentId"))
                .param("ownerUserAccountId", ownerUserAccountId).param("accountId", accountId).param("instrumentId", instrumentId)
                .query(this::mapPositionReadModel).optional();
    }

    private org.springframework.jdbc.core.simple.JdbcClient.StatementSpec tradeSql(String predicate) {
        return jdbcClient.sql("""
                SELECT a.id, s.financial_account_id, account.name AS account_name, s.instrument_id,
                       instrument.symbol, instrument.name AS instrument_name, instrument.instrument_type,
                       s.trade_currency_code, a.activity_type, s.quantity_delta, s.unit_price, s.gross_amount,
                       COALESCE(cash.commission_amount, 0) AS commission_amount,
                       COALESCE(cash.cash_delta, 0) AS cash_delta, a.effective_at, a.recorded_at,
                       a.economic_sequence, a.recording_mode, a.policy_decision, a.source_kind,
                       s.posting_role AS security_posting_role, s.reverses_security_posting_id,
                       reversal.id AS reversal_activity_id, reversal.correction_reason AS reversal_reason,
                       reversal.recorded_at AS reversed_at
                FROM ledger.activity a
                JOIN ledger.security_posting s ON s.owner_user_account_id = a.owner_user_account_id
                    AND s.activity_id = a.id
                JOIN ledger.financial_account account ON account.owner_user_account_id = a.owner_user_account_id
                    AND account.id = s.financial_account_id
                JOIN reference.instrument instrument ON instrument.id = s.instrument_id
                    AND (instrument.owner_user_account_id IS NULL OR instrument.owner_user_account_id = a.owner_user_account_id)
                LEFT JOIN ledger.activity reversal ON reversal.owner_user_account_id = a.owner_user_account_id
                    AND reversal.reverses_activity_id = a.id
                LEFT JOIN (
                    SELECT owner_user_account_id, activity_id,
                           SUM(amount) AS cash_delta,
                           COALESCE(-SUM(amount) FILTER (WHERE posting_role = 'FEE'), 0) AS commission_amount
                    FROM ledger.money_posting
                    GROUP BY owner_user_account_id, activity_id
                ) cash ON cash.owner_user_account_id = a.owner_user_account_id AND cash.activity_id = a.id
                WHERE a.owner_user_account_id = :ownerUserAccountId
                  AND a.activity_type IN ('SECURITY_BUY', 'SECURITY_SELL')
                """ + " AND " + predicate);
    }

    private List<TradeReadModel> toTradeReadModels(UUID ownerUserAccountId, List<TradeRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var postingsByActivity = findTradeCashPostings(ownerUserAccountId, rows.stream().map(TradeRow::id).toList());
        return rows.stream().map(row -> new TradeReadModel(row.id(), row.accountId(), row.accountName(), row.instrumentId(), row.instrumentSymbol(),
                row.instrumentName(), row.instrumentType(), row.currency(), row.side(), row.quantity(), row.unitPrice(), row.grossAmount(),
                row.commissionAmount(), row.cashDelta(), row.quantityDelta(), row.effectiveAt(), row.recordedAt(), row.economicSequence(), row.recordingMode(),
                row.policyDecision(), row.sourceKind(), CalculationPolicy.WEIGHTED_AVERAGE_ECONOMIC_V1, postingsByActivity.getOrDefault(row.id(), List.of()),
                row.securityPosting(), row.reversalActivityId(), row.reversalReason(), row.reversedAt())).toList();
    }

    private Map<UUID, List<PostingResponse>> findTradeCashPostings(UUID ownerUserAccountId, List<UUID> activityIds) {
        var rows = jdbcClient.sql("""
                SELECT activity_id, financial_account_id, cash_pocket_id, currency_code, amount, posting_role
                FROM ledger.money_posting
                WHERE owner_user_account_id = :ownerUserAccountId AND activity_id IN (:activityIds)
                ORDER BY activity_id, CASE posting_role
                    WHEN 'TRADE_PURCHASE' THEN 0
                    WHEN 'TRADE_PROCEEDS' THEN 1
                    WHEN 'FEE' THEN 2
                    ELSE 3
                END, id
                """).param("ownerUserAccountId", ownerUserAccountId).param("activityIds", activityIds)
                .query((resultSet, rowNumber) -> new PostingRow(resultSet.getObject("activity_id", UUID.class),
                        new PostingResponse(resultSet.getObject("financial_account_id", UUID.class), resultSet.getObject("cash_pocket_id", UUID.class),
                                resultSet.getString("currency_code"), FinancialAmount.of(resultSet.getBigDecimal("amount")).canonical(),
                                PostingRole.valueOf(resultSet.getString("posting_role")))))
                .list();
        return rows.stream()
                .collect(Collectors.groupingBy(PostingRow::activityId, LinkedHashMap::new, Collectors.mapping(PostingRow::posting, Collectors.toList())));
    }

    private TradeRow mapTradeRow(ResultSet resultSet, int rowNumber) throws SQLException {
        var side = switch (ActivityType.valueOf(resultSet.getString("activity_type"))) {
            case SECURITY_BUY -> TradeSide.BUY;
            case SECURITY_SELL -> TradeSide.SELL;
            default -> throw new IllegalStateException("Trade read query returned a non-trade activity");
        };
        var quantityDelta = FinancialAmount.of(resultSet.getBigDecimal("quantity_delta"));
        var securityPosting = new SecurityPostingResponse(resultSet.getObject("financial_account_id", UUID.class),
                resultSet.getObject("instrument_id", UUID.class), resultSet.getString("trade_currency_code"), quantityDelta.canonical(),
                FinancialAmount.of(resultSet.getBigDecimal("unit_price")).canonical(), FinancialAmount.of(resultSet.getBigDecimal("gross_amount")).canonical(),
                dev.canverse.stocks.investing.domain.SecurityPostingRole.valueOf(resultSet.getString("security_posting_role")),
                instant(resultSet, "effective_at"), resultSet.getLong("economic_sequence"), resultSet.getObject("reverses_security_posting_id", UUID.class));
        return new TradeRow(resultSet.getObject("id", UUID.class), resultSet.getObject("financial_account_id", UUID.class), resultSet.getString("account_name"),
                resultSet.getObject("instrument_id", UUID.class), resultSet.getString("symbol"), resultSet.getString("instrument_name"),
                InstrumentType.valueOf(resultSet.getString("instrument_type")), resultSet.getString("trade_currency_code"), side,
                FinancialAmount.of(quantityDelta.value().abs()), FinancialAmount.of(resultSet.getBigDecimal("unit_price")),
                FinancialAmount.of(resultSet.getBigDecimal("gross_amount")), FinancialAmount.of(resultSet.getBigDecimal("commission_amount")),
                FinancialAmount.of(resultSet.getBigDecimal("cash_delta")), quantityDelta, instant(resultSet, "effective_at"), instant(resultSet, "recorded_at"),
                resultSet.getLong("economic_sequence"), RecordingMode.valueOf(resultSet.getString("recording_mode")),
                PolicyDecision.valueOf(resultSet.getString("policy_decision")), resultSet.getString("source_kind"), securityPosting,
                resultSet.getObject("reversal_activity_id", UUID.class), resultSet.getString("reversal_reason"), instant(resultSet, "reversed_at"));
    }

    private static TradeEvent mapTradeEvent(ResultSet resultSet) throws SQLException {
        var activityId = resultSet.getObject("activity_id", UUID.class);
        var reversesActivityId = resultSet.getObject("reverses_activity_id", UUID.class);
        var effectiveAt = instant(resultSet, "effective_at");
        var economicSequence = resultSet.getLong("economic_sequence");
        if (reversesActivityId != null) {
            return TradeEvent.reversal(activityId, reversesActivityId, effectiveAt, economicSequence);
        }
        var activityType = ActivityType.valueOf(resultSet.getString("activity_type"));
        var side = activityType == ActivityType.SECURITY_BUY ? TradeSide.BUY : TradeSide.SELL;
        return new TradeEvent(activityId, side, null, FinancialAmount.of(resultSet.getBigDecimal("quantity_delta")),
                FinancialAmount.of(resultSet.getBigDecimal("gross_amount")), FinancialAmount.of(resultSet.getBigDecimal("commission_amount")), effectiveAt,
                economicSequence);
    }

    private PositionReadModel mapPositionReadModel(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PositionReadModel(resultSet.getObject("financial_account_id", UUID.class), resultSet.getString("account_name"),
                resultSet.getObject("instrument_id", UUID.class), resultSet.getString("symbol"), resultSet.getString("instrument_name"),
                InstrumentType.valueOf(resultSet.getString("instrument_type")), resultSet.getString("currency_code"),
                FinancialAmount.of(resultSet.getBigDecimal("current_quantity")), FinancialAmount.of(resultSet.getBigDecimal("remaining_economic_basis")),
                FinancialAmount.of(resultSet.getBigDecimal("cumulative_realized_economic_pnl")),
                CalculationPolicy.valueOf(resultSet.getString("calculation_policy")), ProjectionStatus.valueOf(resultSet.getString("projection_status")),
                instant(resultSet, "as_of"), resultSet.getObject("input_watermark_activity_id", UUID.class), instant(resultSet, "last_successful_build_at"),
                instant(resultSet, "stale_from"), resultSet.getLong("version"));
    }

    private String positionSql(String predicate) {
        return """
                SELECT p.financial_account_id, account.name AS account_name, p.instrument_id,
                       instrument.symbol, instrument.name AS instrument_name, instrument.instrument_type,
                       p.currency_code, p.current_quantity, p.remaining_economic_basis,
                       p.cumulative_realized_economic_pnl, p.calculation_policy, p.projection_status,
                       p.as_of, p.input_watermark_activity_id, p.last_successful_build_at, p.stale_from, p.version
                FROM ledger.position_projection p
                JOIN ledger.financial_account account ON account.owner_user_account_id = p.owner_user_account_id
                    AND account.id = p.financial_account_id
                JOIN reference.instrument instrument ON instrument.id = p.instrument_id
                    AND (instrument.owner_user_account_id IS NULL OR instrument.owner_user_account_id = p.owner_user_account_id)
                WHERE
                """ + predicate;
    }

    private static String tradeOrderBy(Pageable pageable) {
        var orders = pageable.getSort().stream().toList();
        if (orders.isEmpty()) {
            return " ORDER BY a.effective_at DESC, s.economic_sequence DESC, a.id DESC";
        }
        if (orders.size() != 1) {
            throw invalidSort("Trades may be sorted by one documented time property.");
        }
        var order = orders.getFirst();
        validateOrder(order);
        var direction = order.isAscending() ? "ASC" : "DESC";
        return switch (order.getProperty()) {
            case "effectiveAt" -> " ORDER BY a.effective_at " + direction + ", s.economic_sequence " + direction + ", a.id " + direction;
            case "recordedAt" ->
                " ORDER BY a.recorded_at " + direction + ", a.effective_at " + direction + "," + " s.economic_sequence " + direction + ", a.id " + direction;
            default -> throw invalidSort("Trades may be sorted by effectiveAt or recordedAt.");
        };
    }

    private static String positionOrderBy(Pageable pageable) {
        var orders = pageable.getSort().stream().toList();
        if (orders.isEmpty()) {
            return " ORDER BY account.name_normalized ASC, instrument.symbol_normalized ASC, account.id ASC, instrument.id ASC";
        }
        if (orders.size() != 1) {
            throw invalidSort("Positions may be sorted by one documented account or instrument property.");
        }
        var order = orders.getFirst();
        validateOrder(order);
        var direction = order.isAscending() ? "ASC" : "DESC";
        return switch (order.getProperty()) {
            case "accountName" ->
                " ORDER BY account.name_normalized " + direction + ", account.id ASC," + " instrument.symbol_normalized ASC, instrument.id ASC";
            case "instrumentSymbol" ->
                " ORDER BY instrument.symbol_normalized " + direction + ", instrument.id ASC," + " account.name_normalized ASC, account.id ASC";
            default -> throw invalidSort("Positions may be sorted by accountName or instrumentSymbol.");
        };
    }

    private static void validateOrder(Sort.Order order) {
        if ((order.getDirection() != Sort.Direction.ASC && order.getDirection() != Sort.Direction.DESC) || order.isIgnoreCase() ||
                order.getNullHandling() != Sort.NullHandling.NATIVE) {
            throw invalidSort("The sort direction or null handling is not supported.");
        }
    }

    private static AppException invalidSort(String detail) {
        return ValidationErrors.invalidField("sort", "error.fields.investing.invalid_sort", detail);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record TradeRow(UUID id, UUID accountId, String accountName, UUID instrumentId, String instrumentSymbol, String instrumentName,
            InstrumentType instrumentType, String currency, TradeSide side, FinancialAmount quantity, FinancialAmount unitPrice, FinancialAmount grossAmount,
            FinancialAmount commissionAmount, FinancialAmount cashDelta, FinancialAmount quantityDelta, Instant effectiveAt, Instant recordedAt,
            long economicSequence, RecordingMode recordingMode, PolicyDecision policyDecision, String sourceKind, SecurityPostingResponse securityPosting,
            UUID reversalActivityId, String reversalReason, Instant reversedAt) {}

    private record PostingRow(UUID activityId, PostingResponse posting) {}
}
