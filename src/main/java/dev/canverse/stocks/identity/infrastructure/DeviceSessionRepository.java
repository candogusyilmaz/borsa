package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.domain.DeviceSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    @Query(
            "select new dev.canverse.stocks.identity.infrastructure.RefreshSessionOwnerProjection(s.id, s.userAccount.id) "
                    + "from DeviceSession s where s.refreshTokenHash = :refreshTokenHash")
    Optional<RefreshSessionOwnerProjection> findRefreshSessionOwnerByRefreshTokenHash(String refreshTokenHash);

    Optional<DeviceSession> findByRefreshTokenHash(String refreshTokenHash);

    @Query("select s from DeviceSession s where s.id = :sessionId and s.userAccount.id = :userAccountId")
    Optional<DeviceSession> findOwnedById(UUID sessionId, UUID userAccountId);

    Optional<DeviceSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    @Query("""
            select s from DeviceSession s
            where s.userAccount.id = :userAccountId
              and s.familyId = :familyId
              and s.replacedBySessionId is null
            """)
    Optional<DeviceSession> findTerminalByUserAccountIdAndFamilyId(UUID userAccountId, UUID familyId);

    @Query("""
            select s from DeviceSession s
            where s.userAccount.id = :userAccountId
              and s.replacedBySessionId is null
            order by s.familyId asc
            """)
    List<DeviceSession> findTerminalSessionsByUserAccountId(UUID userAccountId);

    @Query("""
            select case when count(s) > 0 then true else false end
            from DeviceSession s
            where s.userAccount.id = :userAccountId
              and s.familyId = :familyId
            """)
    boolean existsByUserAccountIdAndFamilyId(UUID userAccountId, UUID familyId);
}
