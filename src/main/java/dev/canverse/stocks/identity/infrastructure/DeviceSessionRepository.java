package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.domain.DeviceSession;
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

    Optional<DeviceSession> findByIdAndUserAccount_Id(UUID sessionId, UUID userAccountId);

    Optional<DeviceSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
}
