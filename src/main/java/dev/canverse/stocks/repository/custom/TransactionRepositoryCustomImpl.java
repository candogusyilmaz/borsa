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
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryCustomImpl implements TransactionRepositoryCustom {
    private final JPAQueryFactory queryFactory;
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
}
