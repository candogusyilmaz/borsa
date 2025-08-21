package dev.canverse.stocks.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.portfolio.QPortfolio;
import dev.canverse.stocks.domain.entity.portfolio.QPosition;
import dev.canverse.stocks.domain.entity.portfolio.QTransaction;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.service.portfolio.model.MonthlyRevenueOverview;
import dev.canverse.stocks.service.portfolio.model.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryCustomImpl implements TransactionRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final PortfolioRepository portfolioRepository;

    @Override
    public List<MonthlyRevenueOverview> getMonthlyRevenueOverview(long portfolioId) {
        if (!portfolioRepository.isPortfolioOwnedByPrincipal(portfolioId)) {
            throw new NotFoundException("Portfolio not found");
        }

        var transaction = QTransaction.transaction;
        var position = QPosition.position;
        var portfolio = QPortfolio.portfolio;

        var month = transaction.actionDate.month();
        var year = transaction.actionDate.year();
        var q = transaction.performance.profit.sumBigDecimal();

        var groupedData = queryFactory
                .select(
                        month,
                        year,
                        q
                )
                .from(transaction)
                .join(transaction.position, position)
                .join(position.portfolio, portfolio)
                .where(transaction.type.eq(Transaction.Type.SELL).and(portfolio.id.eq(portfolioId)))
                .groupBy(year, month)
                .fetch();

        var dataByYear = groupedData.stream()
                .collect(Collectors.groupingBy(tuple -> tuple.get(year)));

        return dataByYear.entrySet().stream()
                .map(entry -> {
                    int year2 = entry.getKey();
                    List<MonthlyRevenueOverview.Month> months = entry.getValue().stream()
                            .map(tuple -> new MonthlyRevenueOverview.Month(
                                    tuple.get(month),
                                    tuple.get(q)
                            ))
                            .collect(Collectors.toList());
                    return new MonthlyRevenueOverview(year2, months);
                })
                .collect(Collectors.toList());
    }

    @Override
    public TransactionHistory getTransactionHistory(long portfolioId) {
        var transaction = QTransaction.transaction;
        var subTransaction = new QTransaction("subTrade");

        var isLatestExpr = JPAExpressions
                .selectOne()
                .from(subTransaction)
                .where(subTransaction.position.eq(transaction.position).and(subTransaction.id.gt(transaction.id)))
                .notExists();

        var query = queryFactory.select(
                        Projections.constructor(TransactionHistory.Item.class,
                                transaction.actionDate,
                                transaction.createdAt,
                                transaction.type,
                                transaction.position.id.stringValue(),
                                transaction.position.instrument.symbol,
                                transaction.price,
                                transaction.quantity,
                                transaction.performance.profit,
                                transaction.performance.returnPercentage,
                                transaction.performance.performanceCategory,
                                isLatestExpr
                        )
                )
                .from(transaction)
                .leftJoin(transaction.performance)
                .where(transaction.position.portfolio.id.eq(portfolioId))
                .orderBy(transaction.createdAt.desc())
                .fetch();

        return new TransactionHistory(query);
    }


    public RealizedGains getRealizedGains(Long userId, String periodType, String targetCurrency) {
        String sql = """
                WITH period_data AS (
                    SELECT
                        SUM(CASE 
                            WHEN %s THEN 
                                convert_currency(tp.profit, i.denomination_currency, :targetCurrency)
                            ELSE 0
                        END) AS current_period_gain,
                
                        SUM(CASE 
                            WHEN %s THEN 
                                convert_currency(tp.profit, i.denomination_currency, :targetCurrency)
                            ELSE 0
                        END) AS previous_period_gain
                    FROM 
                        portfolio.portfolios p
                        JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                        JOIN instrument.instruments i ON i.id = pos.instrument_id
                        JOIN portfolio.transactions t ON pos.id = t.position_id
                        JOIN portfolio.transaction_performance tp ON t.id = tp.transaction_id
                    WHERE 
                        p.user_id = :userId
                )
                SELECT 
                    COALESCE(current_period_gain, 0) as current_period_gain,
                    COALESCE(previous_period_gain, 0) as previous_period_gain,
                    CASE 
                        WHEN previous_period_gain = 0 THEN NULL
                        ELSE ((current_period_gain - previous_period_gain) / previous_period_gain) * 100
                    END as percentage_change
                FROM period_data
                """.formatted(
                getPeriodCondition(periodType, true),
                getPeriodCondition(periodType, false)
        );

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetCurrency", targetCurrency);

        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            rs.next();
            return new RealizedGains(rs.getBigDecimal("current_period_gain"),
                    rs.getBigDecimal("previous_period_gain"),
                    rs.getBigDecimal("percentage_change"));
        });
    }

    private String getPeriodCondition(String periodType, boolean isCurrent) {
        String interval = isCurrent ? "" : " - INTERVAL '1 %s'".formatted(periodType.toLowerCase());

        return switch (periodType) {
            case "day" -> "t.action_date = CURRENT_DATE" + interval;
            case "week" -> "DATE_TRUNC('week', t.action_date) = DATE_TRUNC('week', CURRENT_DATE" + interval + ")";
            case "month" -> "DATE_TRUNC('month', t.action_date) = DATE_TRUNC('month', CURRENT_DATE" + interval + ")";
            case "year" -> "DATE_TRUNC('year', t.action_date) = DATE_TRUNC('year', CURRENT_DATE" + interval + ")";
            default -> throw new IllegalStateException("Unexpected value: " + periodType);
        };
    }
}
