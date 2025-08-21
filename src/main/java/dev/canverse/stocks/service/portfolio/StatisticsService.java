package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.Calculator;
import dev.canverse.stocks.service.portfolio.model.statistics.DailyChange;
import dev.canverse.stocks.service.portfolio.model.statistics.RealizedGains;
import dev.canverse.stocks.service.portfolio.model.statistics.TotalBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RealizedGains getRealizedGains(long userId, String periodType, String targetCurrency) {
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
                    COALESCE(current_period_gain, 0) as cpg,
                    COALESCE(previous_period_gain, 0) as ppg
                FROM period_data
                """.formatted(
                getPeriodCondition(periodType, true),
                getPeriodCondition(periodType, false)
        );

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            rs.next();
            return new RealizedGains(rs.getBigDecimal("cpg"), rs.getBigDecimal("ppg"),
                    calculatePercentageChange(rs.getBigDecimal("cpg"), rs.getBigDecimal("ppg")), targetCurrency);
        });
    }

    private BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || BigDecimal.ZERO.compareTo(previous) == 0) {
            return null;
        }

        var difference = current.subtract(previous);

        if (previous.compareTo(BigDecimal.ZERO) < 0 && current.compareTo(BigDecimal.ZERO) < 0) {
            var improvementInLoss = previous.subtract(current); // How much loss was reduced/increased
            return Calculator.divide(improvementInLoss, previous.abs())
                    .multiply(BigDecimal.valueOf(100));
        }

        if (previous.compareTo(BigDecimal.ZERO) < 0 && current.compareTo(BigDecimal.ZERO) >= 0) {
            return Calculator.divide(difference, previous.abs())
                    .multiply(BigDecimal.valueOf(100));
        }

        // Standard calculation for other cases
        return Calculator.divide(difference, previous)
                .multiply(BigDecimal.valueOf(100));
    }

    private String getPeriodCondition(String periodType, boolean isCurrent) {
        String interval = isCurrent ? "" : " - INTERVAL '1 %s'".formatted(periodType.toLowerCase());

        return switch (periodType) {
            case "day" -> "DATE_TRUNC('day', t.action_date) = CURRENT_DATE" + interval;
            case "week" -> "DATE_TRUNC('week', t.action_date) = DATE_TRUNC('week', CURRENT_DATE" + interval + ")";
            case "month" -> "DATE_TRUNC('month', t.action_date) = DATE_TRUNC('month', CURRENT_DATE" + interval + ")";
            case "year" -> "DATE_TRUNC('year', t.action_date) = DATE_TRUNC('year', CURRENT_DATE" + interval + ")";
            default -> throw new IllegalStateException("Unexpected value: " + periodType);
        };
    }

    public DailyChange getDailyChange(long userId, Long portfolioId, String targetCurrency) {
        var sql = """
                SELECT
                    -- Current total value of all positions
                    SUM(convert_currency(snp.last, i.denomination_currency, 'TRY') * pos.quantity) AS current_value,
                
                    SUM(convert_currency(snp.previous_close, i.denomination_currency, 'TRY') *
                            (pos.quantity -
                             COALESCE(
                                    (SELECT SUM(t.quantity)
                                     FROM portfolio.transactions t
                                     WHERE t.position_id = pos.id
                                       AND DATE(t.created_at) = CURRENT_DATE), 0))+
                            COALESCE(
                                    (SELECT SUM(convert_currency(t.price, i.denomination_currency, 'TRY') * t.quantity)
                                     FROM portfolio.transactions t
                                     WHERE t.position_id = pos.id
                                       AND DATE(t.created_at) = CURRENT_DATE), 0)) AS previous_close_value
                FROM portfolio.portfolios p
                         JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                         JOIN instrument.instruments i ON i.id = pos.instrument_id
                         JOIN instrument.instrument_snapshots snp ON i.id = snp.instrument_id
                WHERE p.user_id = :userId %s
                """.formatted(portfolioId != null ? "AND p.id = :portfolioId" : "");

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("portfolioId", portfolioId)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return null;
            }

            var currentValue = rs.getBigDecimal("current_value");
            var previousCloseValue = rs.getBigDecimal("previous_close_value");
            var percentageChange = calculatePercentageChange(currentValue, previousCloseValue);

            return new DailyChange(
                    currentValue != null ? currentValue : BigDecimal.ZERO,
                    previousCloseValue != null ? previousCloseValue : BigDecimal.ZERO,
                    percentageChange,
                    targetCurrency
            );
        });
    }

    public TotalBalance getTotalBalance(long userId, Long portfolioId, String targetCurrency) {
        var sql = """
                SELECT
                    SUM(convert_currency(pos.total, i.denomination_currency, :targetCurrency)) AS cost,
                    SUM(convert_currency(snp.last, i.denomination_currency, :targetCurrency) * pos.quantity) AS value
                FROM portfolio.portfolios p
                         JOIN portfolio.positions pos ON p.id = pos.portfolio_id
                         JOIN instrument.instruments i ON i.id = pos.instrument_id
                         JOIN instrument.instrument_snapshots snp ON i.id = snp.instrument_id
                WHERE p.user_id = :userId %s
                """.formatted(portfolioId != null ? "AND p.id = :portfolioId" : "");

        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("portfolioId", portfolioId)
                .addValue("targetCurrency", targetCurrency);

        return jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return new TotalBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, targetCurrency);
            }

            var cost = rs.getBigDecimal("cost");
            var value = rs.getBigDecimal("value");
            var percentageChange = calculatePercentageChange(value, cost);

            return new TotalBalance(value, cost, percentageChange, targetCurrency);
        });
    }
}
