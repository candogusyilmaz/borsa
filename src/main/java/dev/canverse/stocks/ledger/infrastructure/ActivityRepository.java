package dev.canverse.stocks.ledger.infrastructure;

import dev.canverse.stocks.ledger.domain.Activity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    @Query("select a from Activity a where a.id = :activityId and a.ownerUserAccountId = :ownerUserAccountId")
    Optional<Activity> findOwned(UUID activityId, UUID ownerUserAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id = :activityId and a.ownerUserAccountId = :ownerUserAccountId")
    Optional<Activity> findOwnedForUpdate(UUID activityId, UUID ownerUserAccountId);

    @Query(
            "select a from Activity a where a.ownerUserAccountId = :ownerUserAccountId and a.reversesActivityId = :activityId")
    Optional<Activity> findReversal(UUID activityId, UUID ownerUserAccountId);
}
