package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.domain.DeviceSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    Optional<DeviceSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<DeviceSession> findByIdAndUserAccount_Id(UUID sessionId, UUID userAccountId);
}
