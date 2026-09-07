package dev.canverse.stocks.platform.infrastructure;

import dev.canverse.stocks.platform.domain.SecurityEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
}
