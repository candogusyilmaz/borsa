package dev.canverse.stocks.repository.custom;


import dev.canverse.stocks.domain.Calculator;
import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StatisticsRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RealizedGains getRealizedGains(long userId, String periodType, List<Long> portfolioIds, String targetCurrency) {
        if (portfolioIds.isEmpty()) {
            return new RealizedGains(BigDecimal.ZERO, BigDecimal.ZERO, null, targetCurrency);
        }

        var sql = """
                WITH period_data AS (
                    SELECT
                        SUM(CASE
                            WHEN %s THEN
                                convert_currency(tp.profit, pos.currency_code, :targetCurrency)
                            ELSE 0
                        END) AS current_period_gain,
                
                        SUM(CASE
                            WHEN %s THEN
                                convert_currency(tp.profit, pos.currency_code, :targetCurrency)
                            ELSE 0
                        END) AS previous_period_gain
                    FROM
                        portfolio.portfolios p
                        JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                        JOIN instrument.instruments i ON i.id = pos.instrument_id
                        JOIN portfolio.transactions t ON pos.id = t.position_id
                        JOIN portfolio.transaction_performance tp ON t.id = tp.transaction_id
                    WHERE p.user_id = :userId AND p.id IN (:portfolioIds)
                )
                SELECT
                    COALESCE(current_period_gain, 0) as cpg,
                    COALESCE(previous_period_gain, 0) as ppg
                FROM period_data
                """.formatted(
                getPeriodCondition(periodType, true),
                getPeriodCondition(periodType, false)
        );

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("portfolioIds", portfolioIds)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            rs.next();
            return new RealizedGains(rs.getBigDecimal("cpg"), rs.getBigDecimal("ppg"),
                    Calculator.calculatePercentageChange(rs.getBigDecimal("cpg"), rs.getBigDecimal("ppg")), targetCurrency);
        });
    }


    private String getPeriodCondition(String periodType, boolean isCurrent) {
        var interval = isCurrent ? "" : " - INTERVAL '1 %s'".formatted(periodType.toLowerCase());

        return switch (periodType) {
            case "day" -> "DATE_TRUNC('day', t.action_date) = CURRENT_DATE" + interval;
            case "week" -> "DATE_TRUNC('week', t.action_date) = DATE_TRUNC('week', CURRENT_DATE" + interval + ")";
            case "month" -> "DATE_TRUNC('month', t.action_date) = DATE_TRUNC('month', CURRENT_DATE" + interval + ")";
            case "year" -> "DATE_TRUNC('year', t.action_date) = DATE_TRUNC('year', CURRENT_DATE" + interval + ")";
            default -> throw new IllegalStateException("Unexpected value: " + periodType);
        };
    }

    public DailyChange getDailyChange(long userId, List<Long> portfolioIds, String targetCurrency) {
        if (portfolioIds.isEmpty()) {
            return new DailyChange(BigDecimal.ZERO, BigDecimal.ZERO, null, targetCurrency);
        }

        var sql = """
                SELECT
                    -- Today's total value (current market prices * current quantities)
                    SUM(convert_currency(snp.last, pos.currency_code, :targetCurrency) * pos.quantity) AS current_value,
                
                    -- Yesterday's total value (previous close prices * yesterday's quantities)
                    SUM(convert_currency(snp.previous_close, pos.currency_code, :targetCurrency) *
                        COALESCE((SELECT ph.quantity
                                    FROM portfolio.position_history ph
                                    WHERE ph.position_id = pos.id AND DATE(ph.created_at) < CURRENT_DATE
                                    ORDER BY ph.created_at DESC
                                    LIMIT 1
                                 ), 0)
                    ) AS previous_close_value
                FROM
                    portfolio.portfolios p
                        JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                        JOIN instrument.instruments i ON i.id = pos.instrument_id
                        JOIN instrument.instrument_snapshots snp ON i.id = snp.instrument_id and snp.currency_code = pos.currency_code
                WHERE p.user_id = :userId AND pos.quantity > 0 AND p.id IN (:portfolioIds)
                """;

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("portfolioIds", portfolioIds)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return null;
            }

            var currentValue = rs.getBigDecimal("current_value");
            var previousCloseValue = rs.getBigDecimal("previous_close_value");
            var percentageChange = Calculator.calculatePercentageChange(currentValue, previousCloseValue);

            return new DailyChange(
                    currentValue != null ? currentValue : BigDecimal.ZERO,
                    previousCloseValue != null ? previousCloseValue : BigDecimal.ZERO,
                    percentageChange,
                    targetCurrency
            );
        });
    }

    public TotalBalance getTotalBalance(long userId, List<Long> portfolioIds, String targetCurrency) {
        if (portfolioIds.isEmpty()) {
            return new TotalBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, targetCurrency);
        }

        var sql = """
                SELECT
                    SUM(convert_currency(pos.total, pos.currency_code, :targetCurrency)) AS cost,
                    SUM(convert_currency(snp.last, pos.currency_code, :targetCurrency) * pos.quantity) AS value
                FROM portfolio.portfolios p
                         JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                         JOIN instrument.instruments i ON i.id = pos.instrument_id
                         JOIN instrument.instrument_snapshots snp ON i.id = snp.instrument_id and snp.currency_code = pos.currency_code
                WHERE p.user_id = :userId AND p.id IN (:portfolioIds)
                """;

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("portfolioIds", portfolioIds)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return new TotalBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, targetCurrency);
            }

            var cost = rs.getBigDecimal("cost");
            var value = rs.getBigDecimal("value");
            var percentageChange = Calculator.calculatePercentageChange(value, cost);

            return new TotalBalance(value, cost, percentageChange, targetCurrency);
        });
    }
}
