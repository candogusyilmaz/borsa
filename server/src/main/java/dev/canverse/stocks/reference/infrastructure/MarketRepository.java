package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.domain.Market;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, UUID> {
}
