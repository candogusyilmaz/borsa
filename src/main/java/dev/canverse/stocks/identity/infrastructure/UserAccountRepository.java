package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.domain.UserAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {}
