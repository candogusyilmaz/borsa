package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends BaseJpaRepository<Portfolio, Long> {

    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :#{principal.id}")
    List<Portfolio> findMyPortfolios();

    @Query("SELECT p FROM Portfolio p WHERE p.id = :portfolioId AND p.user.id = :#{principal.id}")
    Optional<Portfolio> findPortfolioByPrincipal(long portfolioId);

    @Query("SELECT COUNT(p) > 0 FROM Portfolio p WHERE p.id = :portfolioId AND p.user.id = :#{principal.id}")
    boolean isPortfolioOwnedByPrincipal(long portfolioId);
}