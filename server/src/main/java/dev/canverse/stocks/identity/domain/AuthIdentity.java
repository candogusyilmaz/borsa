package dev.canverse.stocks.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_identity", schema = "identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public static AuthIdentity local(UUID id, UserAccount userAccount, String normalizedEmail, String encodedPasswordHash, Instant registrationTime) {
        var authIdentity = new AuthIdentity();
        authIdentity.id = Objects.requireNonNull(id, "id");
        authIdentity.userAccount = Objects.requireNonNull(userAccount, "userAccount");
        authIdentity.provider = "LOCAL";
        authIdentity.providerSubject = Objects.requireNonNull(normalizedEmail, "normalizedEmail");
        authIdentity.passwordHash = Objects.requireNonNull(encodedPasswordHash, "encodedPasswordHash");
        authIdentity.createdAt = Objects.requireNonNull(registrationTime, "registrationTime");
        authIdentity.updatedAt = registrationTime;
        return authIdentity;
    }
}
