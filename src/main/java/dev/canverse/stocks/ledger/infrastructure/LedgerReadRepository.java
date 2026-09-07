package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.application.model.BalanceView;
import dev.canverse.stocks.ledger.application.model.FinancialAccountView;
import dev.canverse.stocks.ledger.application.model.LastReconciliationSummaryView;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.PostingRole;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.web.response.ActivityResponse;
import dev.canverse.stocks.ledger.web.response.PostingResponse;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.web.SliceResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** Owner-scoped JDBC projections for public ledger responses. */
@Repository
@RequiredArgsConstructor
public class LedgerReadRepository {

    private final JdbcClient jdbcClient;
    private final ReconciliationReadRepository reconciliationReadRepository;

    public Optional<FinancialAccountView> findAccount(UUID ownerUserAccountId, UUID accountId) {
        return jdbcClient
                .sql(accountSql("a.id = :accountId"))
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                .param("accountId", Objects.requireNonNull(accountId, "accountId"))
                .query(this::mapAccount)
                .optional();
    }

    public List<FinancialAccountView> findAccounts(UUID ownerUserAccountId, boolean includeArchived) {
        return jdbcClient
                .sql(accountSql(
                        "(:includeArchived OR a.archived_at IS NULL)" + " ORDER BY a.name_normalized ASC, a.id ASC"))
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                .param("includeArchived", includeArchived)
                .query(this::mapAccount)
                .list();
    }

    public Optional<BalanceView> findBalance(
            UUID ownerUserAccountId, UUID accountId, Instant requestedAsOf, Instant actualAsOf, boolean current) {
        var account = findAccount(ownerUserAccountId, accountId);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        var row = account.get();
        if (row.coverageStatus() == CoverageStatus.UNTRACKED) {
            return Optional.of(new BalanceView(
                    accountId,
                    requestedAsOf,
                    actualAsOf,
                    row.currencyCode(),
                    row.coverageStatus(),
                    null,
                    row.sourceKind(),
                    ProjectionStatus.NOT_APPLICABLE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null));
        }

        var projection = jdbcClient
                .sql("""
                        SELECT p.ledger_balance, p.last_applied_recorded_at, p.last_applied_activity_id
                        FROM ledger.account_balance_projection p
                        WHERE p.owner_user_account_id = :ownerUserAccountId
                          AND p.financial_account_id = :accountId
                        """)
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("accountId", accountId)
                .query((resultSet, rowNumber) -> new ProjectionRow(
                        resultSet.getBigDecimal("ledger_balance"),
                        instant(resultSet, "last_applied_recorded_at"),
                        resultSet.getObject("last_applied_activity_id", UUID.class)))
                .optional();
        if (projection.isEmpty()) {
            return Optional.empty();
        }

        var balance = current
                ? projection.get().ledgerBalance()
                : historicalBalance(ownerUserAccountId, accountId, requestedAsOf, row.coverageFrom());
        var beforeCoverage = requestedAsOf.isBefore(row.coverageFrom());
        if (beforeCoverage) {
            balance = null;
        }
        var lastReconciliation = reconciliationReadRepository
                .findLatestSummary(ownerUserAccountId, accountId)
                .orElse(null);
        return Optional.of(toBalance(
                row,
                requestedAsOf,
                actualAsOf,
                balance,
                projection.get().recordedAt(),
                projection.get().activityId(),
                current ? ProjectionStatus.CURRENT : ProjectionStatus.NOT_APPLICABLE,
                lastReconciliation));
    }

    public SliceResponse<ActivityResponse> findActivities(UUID ownerUserAccountId, UUID accountId, Pageable pageable) {
        var pageSize = Objects.requireNonNull(pageable, "pageable").getPageSize();
        var sql = """
                        SELECT DISTINCT a.id, a.owner_user_account_id, a.activity_type, a.recording_mode, a.effective_at, a.recorded_at,
                       a.policy_decision, a.source_kind, a.reverses_activity_id, a.supersedes_activity_id
                FROM ledger.activity a
                JOIN ledger.money_posting p ON p.owner_user_account_id = a.owner_user_account_id
                    AND p.activity_id = a.id
                WHERE a.owner_user_account_id = :ownerUserAccountId
                """
                + (accountId == null ? "" : " AND p.financial_account_id = :accountId ")
                + activityOrderBy(pageable)
                + " LIMIT :fetchLimit OFFSET :offset";
        var statement = jdbcClient
                .sql(sql)
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                .param("fetchLimit", pageSize + 1)
                .param("offset", pageable.getOffset());
        if (accountId != null) {
            statement = statement.param("accountId", accountId);
        }
        var rows = statement.query(this::mapActivityRow).list();
        var hasNext = rows.size() > pageSize;
        var pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        return new SliceResponse<>(mapActivities(pageRows), pageable.getPageNumber(), pageSize, hasNext);
    }

    private static String activityOrderBy(Pageable pageable) {
        var orders = pageable.getSort().stream().toList();
        if (orders.size() != 1) {
            throw invalidSort();
        }
        var order = orders.getFirst();
        if ((order.getDirection() != Sort.Direction.ASC && order.getDirection() != Sort.Direction.DESC)
                || order.isIgnoreCase()
                || order.getNullHandling() != Sort.NullHandling.NATIVE) {
            throw invalidSort();
        }
        return switch (order.getProperty()) {
            case "recordedAt" ->
                order.isAscending()
                        ? " ORDER BY a.recorded_at ASC, a.id ASC"
                        : " ORDER BY a.recorded_at DESC, a.id DESC";
            case "effectiveAt" ->
                order.isAscending()
                        ? " ORDER BY a.effective_at ASC, a.id ASC"
                        : " ORDER BY a.effective_at DESC, a.id DESC";
            default -> throw invalidSort();
        };
    }

    private static AppException invalidSort() {
        return ValidationErrors.invalidField(
                "sort",
                "error.fields.ledger.invalid_sort",
                "The sort must contain exactly one supported property and direction.");
    }

    public Optional<ActivityResponse> findActivity(UUID ownerUserAccountId, UUID activityId) {
        return jdbcClient
                .sql("""
                        SELECT a.id, a.owner_user_account_id, a.activity_type, a.recording_mode, a.effective_at, a.recorded_at,
                               a.policy_decision, a.source_kind, a.reverses_activity_id, a.supersedes_activity_id
                        FROM ledger.activity a
                        WHERE a.owner_user_account_id = :ownerUserAccountId AND a.id = :activityId
                        """)
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("activityId", activityId)
                .query(this::mapActivityRow)
                .optional()
                .map(row -> mapActivities(List.of(row)).getFirst());
    }

    private String accountSql(String predicate) {
        return """
                SELECT a.id, a.name, a.account_kind, a.tracking_mode, a.currency_code, a.time_zone,
                       a.negative_balance_policy, a.authorized_limit, a.archived_at, a.version,
                       a.created_at, a.updated_at,
                       COALESCE(p.coverage_status, 'UNTRACKED') AS coverage_status,
                       p.coverage_from,
                       bp.ledger_balance AS current_ledger_balance
                FROM ledger.financial_account a
                LEFT JOIN ledger.account_cash_pocket p ON p.owner_user_account_id = a.owner_user_account_id
                    AND p.financial_account_id = a.id
                LEFT JOIN ledger.account_balance_projection bp ON bp.owner_user_account_id = a.owner_user_account_id
                    AND bp.financial_account_id = a.id
                WHERE a.owner_user_account_id = :ownerUserAccountId AND """ + " " + predicate;
    }

    private FinancialAccountView mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        var coverageStatus = CoverageStatus.valueOf(resultSet.getString("coverage_status"));
        var accountKind = AccountKind.valueOf(resultSet.getString("account_kind"));
        var policy = enumOrNull(resultSet.getString("negative_balance_policy"), NegativeBalancePolicy.class);
        var authorizedLimit = resultSet.getBigDecimal("authorized_limit");
        return new FinancialAccountView(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                accountKind,
                TrackingMode.valueOf(resultSet.getString("tracking_mode")),
                resultSet.getString("currency_code"),
                resultSet.getString("time_zone"),
                policy,
                canonicalNullable(authorizedLimit),
                instant(resultSet, "archived_at"),
                coverageStatus,
                instant(resultSet, "coverage_from"),
                "USER_ENTERED",
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                policyBreach(accountKind, policy, resultSet.getBigDecimal("current_ledger_balance"), authorizedLimit));
    }

    private ActivityRow mapActivityRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ActivityRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("owner_user_account_id", UUID.class),
                ActivityType.valueOf(resultSet.getString("activity_type")),
                RecordingMode.valueOf(resultSet.getString("recording_mode")),
                instant(resultSet, "effective_at"),
                instant(resultSet, "recorded_at"),
                PolicyDecision.valueOf(resultSet.getString("policy_decision")),
                resultSet.getString("source_kind"),
                resultSet.getObject("reverses_activity_id", UUID.class),
                resultSet.getObject("supersedes_activity_id", UUID.class));
    }

    private List<ActivityResponse> mapActivities(List<ActivityRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var postingsByActivity = findPostings(
                rows.getFirst().ownerUserAccountId(),
                rows.stream().map(ActivityRow::id).toList());
        return rows.stream()
                .map(row -> new ActivityResponse(
                        row.id(),
                        row.activityType(),
                        row.recordingMode(),
                        row.effectiveAt(),
                        row.recordedAt(),
                        row.policyDecision(),
                        row.sourceKind(),
                        row.reversesActivityId(),
                        row.supersedesActivityId(),
                        postingsByActivity.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private Map<UUID, List<PostingResponse>> findPostings(UUID ownerUserAccountId, List<UUID> activityIds) {
        var rows = jdbcClient
                .sql("""
                        SELECT activity_id, financial_account_id, cash_pocket_id, currency_code, amount, posting_role
                        FROM ledger.money_posting
                        WHERE owner_user_account_id = :ownerUserAccountId AND activity_id IN (:activityIds)
                        ORDER BY CASE posting_role
                            WHEN 'TRANSFER_SOURCE' THEN 0
                            WHEN 'TRANSFER_DESTINATION' THEN 1
                            WHEN 'OPENING' THEN 2
                            WHEN 'DEPOSIT' THEN 3
                            WHEN 'WITHDRAWAL' THEN 4
                            WHEN 'REVERSAL' THEN 5
                            ELSE 6
                        END, financial_account_id, id
                        """)
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("activityIds", activityIds)
                .query((resultSet, rowNumber) -> new PostingRow(
                        resultSet.getObject("activity_id", UUID.class),
                        new PostingResponse(
                                resultSet.getObject("financial_account_id", UUID.class),
                                resultSet.getObject("cash_pocket_id", UUID.class),
                                resultSet.getString("currency_code"),
                                FinancialAmount.of(resultSet.getBigDecimal("amount"))
                                        .canonical(),
                                PostingRole.valueOf(resultSet.getString("posting_role")))))
                .list();
        return rows.stream()
                .collect(Collectors.groupingBy(
                        PostingRow::activityId,
                        LinkedHashMap::new,
                        Collectors.mapping(PostingRow::posting, Collectors.toList())));
    }

    private BigDecimal historicalBalance(UUID ownerUserAccountId, UUID accountId, Instant asOf, Instant coverageFrom) {
        if (asOf.isBefore(coverageFrom)) {
            return null;
        }
        return jdbcClient
                .sql("""
                        SELECT COALESCE(SUM(p.amount), 0) AS balance
                        FROM ledger.money_posting p
                        JOIN ledger.activity a ON a.owner_user_account_id = p.owner_user_account_id
                            AND a.id = p.activity_id
                        WHERE p.owner_user_account_id = :ownerUserAccountId
                          AND p.financial_account_id = :accountId
                          AND a.effective_at <= :asOf
                        """)
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("accountId", accountId)
                .param("asOf", OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC))
                .query(BigDecimal.class)
                .single();
    }

    private BalanceView toBalance(
            FinancialAccountView account,
            Instant requestedAsOf,
            Instant actualAsOf,
            BigDecimal balance,
            Instant watermarkRecordedAt,
            UUID watermarkActivityId,
            ProjectionStatus projectionStatus,
            LastReconciliationSummaryView lastReconciliation) {
        var effectiveProjectionStatus = balance == null ? ProjectionStatus.NOT_APPLICABLE : projectionStatus;
        var effectiveWatermarkRecordedAt =
                effectiveProjectionStatus == ProjectionStatus.CURRENT ? watermarkRecordedAt : null;
        var effectiveWatermarkActivityId =
                effectiveProjectionStatus == ProjectionStatus.CURRENT ? watermarkActivityId : null;
        if (balance == null) {
            return new BalanceView(
                    account.id(),
                    requestedAsOf,
                    actualAsOf,
                    account.currencyCode(),
                    account.coverageStatus(),
                    account.coverageFrom(),
                    account.sourceKind(),
                    effectiveProjectionStatus,
                    effectiveWatermarkRecordedAt,
                    effectiveWatermarkActivityId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    lastReconciliation);
        }
        var exact = FinancialAmount.of(balance);
        var zero = FinancialAmount.zero();
        var cashHeld = exact.compareTo(zero) > 0 ? exact : zero;
        var negative = exact.compareTo(zero) < 0 ? exact.negate() : zero;
        var liability = account.accountKind().isLiability() ? (exact.isPositive() ? exact : zero) : zero;
        var authorizedLimit =
                account.authorizedLimit() == null ? null : FinancialAmount.parse(account.authorizedLimit());
        var creditAvailable = account.negativeBalancePolicy() == NegativeBalancePolicy.AUTHORIZED_LIMIT
                ? authorizedLimit.subtract(negative)
                : null;
        if (creditAvailable != null && creditAvailable.isNegative()) {
            creditAvailable = zero;
        }
        return new BalanceView(
                account.id(),
                requestedAsOf,
                actualAsOf,
                account.currencyCode(),
                account.coverageStatus(),
                account.coverageFrom(),
                account.sourceKind(),
                effectiveProjectionStatus,
                effectiveWatermarkRecordedAt,
                effectiveWatermarkActivityId,
                exact.canonical(),
                exact.canonical(),
                account.accountKind().isLiability() ? zero.canonical() : cashHeld.canonical(),
                account.accountKind().isLiability() ? liability.canonical() : null,
                account.accountKind().isLiability() ? null : negative.canonical(),
                creditAvailable == null ? null : creditAvailable.canonical(),
                policyBreach(
                        account.accountKind(),
                        account.negativeBalancePolicy(),
                        balance,
                        authorizedLimit == null ? null : authorizedLimit.value()),
                lastReconciliation);
    }

    private static boolean policyBreach(
            AccountKind accountKind, NegativeBalancePolicy policy, BigDecimal balance, BigDecimal authorizedLimit) {
        if (balance == null || balance.signum() >= 0) {
            return false;
        }
        if (accountKind.isLiability()) {
            return true;
        }
        if (policy == null) {
            return false;
        }
        if (policy == NegativeBalancePolicy.AUTHORIZED_LIMIT) {
            return authorizedLimit != null && balance.compareTo(authorizedLimit.negate()) < 0;
        }
        return true;
    }

    private static String canonicalNullable(BigDecimal value) {
        return value == null ? null : FinancialAmount.of(value).canonical();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record ProjectionRow(BigDecimal ledgerBalance, Instant recordedAt, UUID activityId) {}

    private record ActivityRow(
            UUID id,
            UUID ownerUserAccountId,
            ActivityType activityType,
            RecordingMode recordingMode,
            Instant effectiveAt,
            Instant recordedAt,
            PolicyDecision policyDecision,
            String sourceKind,
            UUID reversesActivityId,
            UUID supersedesActivityId) {}

    private record PostingRow(UUID activityId, PostingResponse posting) {}
}
