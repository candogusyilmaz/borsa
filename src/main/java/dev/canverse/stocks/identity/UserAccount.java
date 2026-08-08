package dev.canverse.stocks.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account", schema = "identity")
public class UserAccount {

    @Id
    private UUID id;

    private String email;

    private String emailNormalized;

    private Instant disabledAt;

    private Instant createdAt;

    private Instant updatedAt;

    protected UserAccount() {}

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
