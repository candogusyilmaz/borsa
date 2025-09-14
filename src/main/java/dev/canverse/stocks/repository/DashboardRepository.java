package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashboardRepository extends BaseJpaRepository<Dashboard, Long> {
    @Query("select d from Dashboard d where d.isDefault = true and d.user.id = :userId")
    Dashboard findDefaultByUserId(@NonNull Long userId);

    @Query("select d from Dashboard d where d.id = :dashboardId and d.user.id = :userId")
    @EntityGraph(attributePaths = {"dashboardPortfolios.portfolio.positions.transactions.position.instrument"})
    Dashboard getByIdIncludePortfolioTransactions(Long userId, Long dashboardId);

    @Query("select d from Transaction d join d.position.portfolio where d.position.portfolio.id in (:portfolioIds)")
    List<Transaction> getByIdIncludePortfolioTransactions2(List<Long> portfolioIds);
}