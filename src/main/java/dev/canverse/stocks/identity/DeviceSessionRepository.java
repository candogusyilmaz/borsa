package dev.canverse.stocks.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {}
