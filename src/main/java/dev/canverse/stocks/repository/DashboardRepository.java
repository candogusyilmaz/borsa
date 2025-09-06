package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardRepository extends BaseJpaRepository<Dashboard, Long> {
    @Query("select d from Dashboard d where d.isDefault = true and d.user.id = :userId")
    Dashboard findDefaultByUserId(@NonNull Long userId);
}