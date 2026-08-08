package dev.canverse.stocks.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {}
