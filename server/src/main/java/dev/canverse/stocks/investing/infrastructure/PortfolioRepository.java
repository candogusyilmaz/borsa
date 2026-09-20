package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.Portfolio;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    @Query("select p from Portfolio p where p.id = :portfolioId and p.ownerUserAccountId = :ownerUserAccountId")
    Optional<Portfolio> findOwned(UUID ownerUserAccountId, UUID portfolioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :portfolioId and p.ownerUserAccountId = :ownerUserAccountId")
    Optional<Portfolio> findOwnedForUpdate(UUID ownerUserAccountId, UUID portfolioId);

    @Query("select count(p) > 0 from Portfolio p where p.id = :portfolioId and p.ownerUserAccountId = :ownerUserAccountId")
    boolean existsOwned(UUID ownerUserAccountId, UUID portfolioId);

    @Query("select count(p) > 0 from Portfolio p where p.ownerUserAccountId = :ownerUserAccountId and" +
            " p.nameNormalized = :nameNormalized and p.archivedAt is null and" + " (:excludedPortfolioId is null or p.id <> :excludedPortfolioId)")
    boolean existsActiveName(UUID ownerUserAccountId, String nameNormalized, UUID excludedPortfolioId);
}
