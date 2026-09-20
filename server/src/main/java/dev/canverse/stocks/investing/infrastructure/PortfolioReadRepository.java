package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.application.model.PortfolioSummaryView;
import dev.canverse.stocks.investing.web.response.PortfolioAccountResponse;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import dev.canverse.stocks.investing.web.response.PortfolioSummaryResponse;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioReadRepository {

    private static final String SUMMARY_SQL = """
            SELECT p.id, p.name, COUNT(m.id) AS account_count, p.version, p.created_at, p.updated_at, p.archived_at
            FROM ledger.portfolio p
            LEFT JOIN ledger.portfolio_account_membership m
                ON m.owner_user_account_id = p.owner_user_account_id AND m.portfolio_id = p.id
            WHERE p.owner_user_account_id = :ownerUserAccountId
            """;

    private static final String DETAIL_SQL = """
            SELECT p.id, p.name, p.version, p.created_at, p.updated_at, p.archived_at,
                   a.id AS account_id, a.name AS account_name, a.account_kind, a.tracking_mode, a.currency_code, a.archived_at AS account_archived_at
            FROM ledger.portfolio p
            LEFT JOIN ledger.portfolio_account_membership m
                ON m.owner_user_account_id = p.owner_user_account_id AND m.portfolio_id = p.id
            LEFT JOIN ledger.financial_account a
                ON a.owner_user_account_id = m.owner_user_account_id AND a.id = m.financial_account_id
            WHERE p.owner_user_account_id = :ownerUserAccountId AND p.id = :portfolioId
            ORDER BY a.name_normalized ASC NULLS LAST, a.id ASC NULLS LAST
            """;

    private final JdbcClient jdbcClient;

    public List<PortfolioSummaryResponse> findSummaries(UUID ownerUserAccountId, boolean includeArchived) {
        var sql = SUMMARY_SQL + (includeArchived ? "" : " AND p.archived_at IS NULL") + " GROUP BY p.id ORDER BY p.name_normalized ASC, p.id ASC";
        return jdbcClient.sql(sql).param("ownerUserAccountId", ownerUserAccountId).query(PortfolioReadRepository::mapSummary).list();
    }

    public Optional<PortfolioResponse> findDetail(UUID ownerUserAccountId, UUID portfolioId) {
        var rows = jdbcClient.sql(DETAIL_SQL).param("ownerUserAccountId", ownerUserAccountId).param("portfolioId", portfolioId)
                .query(PortfolioReadRepository::mapDetailRow).list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        var first = rows.getFirst();
        var accounts = rows.stream().filter(row -> row.accountId() != null)
                .map(row -> new PortfolioAccountResponse(row.accountId(), row.accountName(), AccountKind.valueOf(row.accountKind()),
                        TrackingMode.valueOf(row.trackingMode()), row.currency(), row.accountArchivedAt() != null, row.accountArchivedAt()))
                .toList();
        return Optional.of(PortfolioResponse.from(first.portfolio(), accounts));
    }

    private static PortfolioSummaryResponse mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        var view = mapPortfolio(resultSet);
        return PortfolioSummaryResponse.from(new PortfolioSummaryView(view.id(), view.name(), resultSet.getInt("account_count"), view.version(),
                view.createdAt(), view.updatedAt(), view.archivedAt()));
    }

    private static PortfolioDetailRow mapDetailRow(ResultSet resultSet, int rowNumber) throws SQLException {
        var portfolio = mapPortfolio(resultSet);
        var accountId = resultSet.getObject("account_id", UUID.class);
        return new PortfolioDetailRow(portfolio, accountId, resultSet.getString("account_name"), resultSet.getString("account_kind"),
                resultSet.getString("tracking_mode"), resultSet.getString("currency_code"), instant(resultSet, "account_archived_at"));
    }

    private static PortfolioSummaryView mapPortfolio(ResultSet resultSet) throws SQLException {
        return new PortfolioSummaryView(resultSet.getObject("id", UUID.class), resultSet.getString("name"), 0, resultSet.getLong("version"),
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"), instant(resultSet, "archived_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record PortfolioDetailRow(PortfolioSummaryView portfolio, UUID accountId, String accountName, String accountKind, String trackingMode,
            String currency, Instant accountArchivedAt) {}
}
