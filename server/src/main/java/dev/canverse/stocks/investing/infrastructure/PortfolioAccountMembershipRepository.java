package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.PortfolioAccountMembership;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioAccountMembershipRepository extends JpaRepository<PortfolioAccountMembership, UUID> {

    @Modifying
    @Query("delete from PortfolioAccountMembership m where m.ownerUserAccountId = :ownerUserAccountId and m.portfolioId = :portfolioId")
    int deleteOwned(UUID ownerUserAccountId, UUID portfolioId);
}
