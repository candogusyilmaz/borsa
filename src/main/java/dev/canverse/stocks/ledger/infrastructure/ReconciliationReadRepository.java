package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.application.model.LastReconciliationSummaryView;
import dev.canverse.stocks.ledger.application.model.ReconciliationCursor;
import dev.canverse.stocks.ledger.application.model.ReconciliationPreviewView;
import dev.canverse.stocks.ledger.application.model.ReconciliationView;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.ReconciliationLifecycleStatus;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Explicit SQL read models for reconciliation comparisons and lifecycle state. */
@Repository
@RequiredArgsConstructor
public class ReconciliationReadRepository {

    private final JdbcClient jdbcClient;

    public Optional<ReconciliationPreviewView> findPreview(
            UUID ownerUserAccountId,
            UUID accountId,
            String statementReference,
            Instant statementOpeningAt,
            Instant statementClosingAt,
            FinancialAmount statementOpeningBalance,
            FinancialAmount statementClosingBalance) {
        return jdbcClient
                .sql("""
                        SELECT a.id,
                               p.id AS cash_pocket_id,
                               a.currency_code,
                               COALESCE(p.coverage_status, 'UNTRACKED') AS coverage_status,
                               p.coverage_from,
                               bp.version AS projection_version,
                               COALESCE(SUM(mp.amount) FILTER (WHERE act.effective_at <= :statementOpeningAt), 0) AS ledger_opening_balance,
                               COALESCE(SUM(mp.amount) FILTER (WHERE act.effective_at <= :statementClosingAt), 0) AS ledger_closing_balance,
                               COALESCE(SUM(mp.amount) FILTER (WHERE act.effective_at > :statementOpeningAt
                                                                  AND act.effective_at <= :statementClosingAt), 0) AS period_net_posted_amount,
                               COUNT(mp.id) FILTER (WHERE act.effective_at > :statementOpeningAt
                                                     AND act.effective_at <= :statementClosingAt) AS period_posting_count,
                               COUNT(mp.id) FILTER (WHERE act.effective_at <= :statementClosingAt) AS total_posting_count
                        FROM ledger.financial_account a
                        LEFT JOIN ledger.account_cash_pocket p
                          ON p.owner_user_account_id = a.owner_user_account_id
                         AND p.financial_account_id = a.id
                        LEFT JOIN ledger.account_balance_projection bp
                          ON bp.owner_user_account_id = a.owner_user_account_id
                         AND bp.financial_account_id = a.id
                        LEFT JOIN ledger.money_posting mp
                          ON mp.owner_user_account_id = a.owner_user_account_id
                         AND mp.financial_account_id = a.id
                        LEFT JOIN ledger.activity act
                          ON act.owner_user_account_id = mp.owner_user_account_id
                         AND act.id = mp.activity_id
                        WHERE a.owner_user_account_id = :ownerUserAccountId
                          AND a.id = :accountId
                        GROUP BY a.id, p.id, p.coverage_status, p.coverage_from, bp.version
                        """)
                .param("ownerUserAccountId", Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId"))
                .param("accountId", Objects.requireNonNull(accountId, "accountId"))
                .param("statementOpeningAt", timestamp(statementOpeningAt))
                .param("statementClosingAt", timestamp(statementClosingAt))
                .query((resultSet, rowNumber) -> mapPreview(
                        resultSet,
                        statementReference,
                        statementOpeningAt,
                        statementClosingAt,
                        statementOpeningBalance,
                        statementClosingBalance))
                .optional();
    }

    public Optional<ReconciliationView> findDetail(UUID ownerUserAccountId, UUID reconciliationId) {
        return jdbcClient
                .sql(detailSql("r.id = :reconciliationId"))
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("reconciliationId", reconciliationId)
                .query(this::mapDetail)
                .optional();
    }

    public List<ReconciliationView> findPage(
            UUID ownerUserAccountId, UUID accountId, ReconciliationCursor cursor, int limit) {
        var predicate = "r.financial_account_id = :accountId";
        if (cursor != null) {
            predicate += " AND (r.statement_closing_at, r.id) < (:cursorClosingAt, :cursorId)";
        }
        var statement = jdbcClient
                .sql(detailSql(predicate) + " ORDER BY r.statement_closing_at DESC, r.id DESC LIMIT :limit")
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("accountId", accountId)
                .param("limit", limit);
        if (cursor != null) {
            statement = statement
                    .param("cursorClosingAt", timestamp(cursor.statementClosingAt()))
                    .param("cursorId", cursor.reconciliationId());
        }
        return statement.query(this::mapDetail).list();
    }

    public Optional<LastReconciliationSummaryView> findLatestSummary(UUID ownerUserAccountId, UUID accountId) {
        return jdbcClient
                .sql(detailSql("r.financial_account_id = :accountId"
                                + " AND NOT EXISTS (SELECT 1 FROM ledger.reconciliation replacement"
                                + " WHERE replacement.owner_user_account_id = r.owner_user_account_id"
                                + " AND replacement.supersedes_reconciliation_id = r.id)")
                        + " ORDER BY r.statement_closing_at DESC, r.created_at DESC, r.id DESC LIMIT 1")
                .param("ownerUserAccountId", ownerUserAccountId)
                .param("accountId", accountId)
                .query(this::mapSummary)
                .optional();
    }

    private String detailSql(String predicate) {
        return """
                SELECT r.id,
                       r.financial_account_id,
                       r.cash_pocket_id,
                       r.currency_code,
                       r.statement_reference,
                       r.statement_opening_at,
                       r.statement_closing_at,
                       r.statement_opening_balance,
                       r.statement_closing_balance,
                       r.ledger_opening_balance,
                       r.ledger_closing_balance_before_adjustment,
                       r.period_net_posted_amount,
                       r.closing_difference,
                       r.adjustment_amount,
                       r.period_posting_count,
                       r.total_posting_count_through_closing,
                       r.resolution,
                       r.adjustment_activity_id,
                       r.adjustment_reason,
                       r.supersedes_reconciliation_id,
                       r.source_kind,
                       r.created_at,
                       CASE
                           WHEN EXISTS (
                               SELECT 1
                               FROM ledger.reconciliation replacement
                               WHERE replacement.owner_user_account_id = r.owner_user_account_id
                                 AND replacement.supersedes_reconciliation_id = r.id
                           ) THEN 'SUPERSEDED'
                           WHEN (
                               SELECT COUNT(*)
                               FROM ledger.money_posting later_posting
                               JOIN ledger.activity later_activity
                                 ON later_activity.owner_user_account_id = later_posting.owner_user_account_id
                                AND later_activity.id = later_posting.activity_id
                               WHERE later_posting.owner_user_account_id = r.owner_user_account_id
                                 AND later_posting.financial_account_id = r.financial_account_id
                                 AND later_activity.effective_at <= r.statement_closing_at
                           ) > r.total_posting_count_through_closing THEN 'STALE'
                           ELSE 'CURRENT'
                       END AS lifecycle_status
                FROM ledger.reconciliation r
                WHERE r.owner_user_account_id = :ownerUserAccountId""" + " AND " + predicate;
    }

    private ReconciliationPreviewView mapPreview(
            ResultSet resultSet,
            String statementReference,
            Instant statementOpeningAt,
            Instant statementClosingAt,
            FinancialAmount statementOpeningBalance,
            FinancialAmount statementClosingBalance)
            throws SQLException {
        var ledgerOpening = amount(resultSet, "ledger_opening_balance");
        var ledgerClosing = amount(resultSet, "ledger_closing_balance");
        var openingDifference = statementOpeningBalance.subtract(ledgerOpening);
        var closingDifference = statementClosingBalance.subtract(ledgerClosing);
        var admissible = openingDifference.isZero()
                ? closingDifference.isZero() ? List.of("CONFIRM_BALANCED") : List.of("CREATE_ADJUSTMENT")
                : List.<String>of();
        var warnings = openingDifference.isZero() ? List.<String>of() : List.of("RECONCILIATION_OPENING_MISMATCH");
        var version = resultSet.getObject("projection_version", Long.class);
        return new ReconciliationPreviewView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("cash_pocket_id", UUID.class),
                resultSet.getString("currency_code"),
                CoverageStatus.valueOf(resultSet.getString("coverage_status")),
                instant(resultSet, "coverage_from"),
                statementReference,
                statementOpeningAt,
                statementClosingAt,
                statementOpeningBalance,
                statementClosingBalance,
                ledgerOpening,
                ledgerClosing,
                openingDifference,
                amount(resultSet, "period_net_posted_amount"),
                closingDifference,
                resultSet.getLong("period_posting_count"),
                resultSet.getLong("total_posting_count"),
                version == null ? 0 : version,
                admissible,
                warnings);
    }

    private ReconciliationView mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReconciliationView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("financial_account_id", UUID.class),
                resultSet.getObject("cash_pocket_id", UUID.class),
                resultSet.getString("currency_code"),
                resultSet.getString("statement_reference"),
                instant(resultSet, "statement_opening_at"),
                instant(resultSet, "statement_closing_at"),
                canonical(resultSet, "statement_opening_balance"),
                canonical(resultSet, "statement_closing_balance"),
                canonical(resultSet, "ledger_opening_balance"),
                canonical(resultSet, "ledger_closing_balance_before_adjustment"),
                difference(resultSet, "statement_opening_balance", "ledger_opening_balance"),
                canonical(resultSet, "period_net_posted_amount"),
                canonical(resultSet, "closing_difference"),
                nullableCanonical(resultSet, "adjustment_amount"),
                resultSet.getLong("period_posting_count"),
                resultSet.getLong("total_posting_count_through_closing"),
                ReconciliationResolution.valueOf(resultSet.getString("resolution")),
                resultSet.getObject("adjustment_activity_id", UUID.class),
                resultSet.getString("adjustment_reason"),
                resultSet.getObject("supersedes_reconciliation_id", UUID.class),
                ReconciliationLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                resultSet.getString("source_kind"),
                instant(resultSet, "created_at"));
    }

    private LastReconciliationSummaryView mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LastReconciliationSummaryView(
                resultSet.getObject("id", UUID.class),
                instant(resultSet, "statement_closing_at"),
                canonical(resultSet, "statement_closing_balance"),
                ReconciliationResolution.valueOf(resultSet.getString("resolution")),
                ReconciliationLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                instant(resultSet, "created_at"));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static FinancialAmount amount(ResultSet resultSet, String column) throws SQLException {
        return FinancialAmount.of(resultSet.getBigDecimal(column));
    }

    private static String canonical(ResultSet resultSet, String column) throws SQLException {
        return amount(resultSet, column).canonical();
    }

    private static String nullableCanonical(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getBigDecimal(column);
        return value == null ? null : FinancialAmount.of(value).canonical();
    }

    private static String difference(ResultSet resultSet, String left, String right) throws SQLException {
        return FinancialAmount.of(resultSet.getBigDecimal(left))
                .subtract(FinancialAmount.of(resultSet.getBigDecimal(right)))
                .canonical();
    }
}
