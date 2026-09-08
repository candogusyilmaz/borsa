package dev.canverse.stocks.platform.infrastructure;

import dev.canverse.stocks.platform.domain.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, UUID> {
}
