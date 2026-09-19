package dev.canverse.stocks.investing.infrastructure;

import dev.canverse.stocks.investing.domain.SecurityPosting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SecurityPostingRepository extends JpaRepository<SecurityPosting, UUID> {

    @Query("select p from SecurityPosting p where p.ownerUserAccountId = :ownerUserAccountId and p.activityId = :activityId")
    Optional<SecurityPosting> findOwnedActivity(UUID ownerUserAccountId, UUID activityId);
}
