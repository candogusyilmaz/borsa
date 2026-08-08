package dev.canverse.stocks.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_identity", schema = "identity")
public class AuthIdentity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id")
    private UserAccount userAccount;

    private String provider;

    private String providerSubject;

    private String passwordHash;

    private Instant createdAt;

    private Instant updatedAt;

    protected AuthIdentity() {}

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
