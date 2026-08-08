package dev.canverse.stocks.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_account", schema = "identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    private UUID id;

    private String email;

    private String emailNormalized;

    private Instant disabledAt;

    private Instant createdAt;

    private Instant updatedAt;

    public static UserAccount register(UUID id, String email, String emailNormalized, Instant registrationTime) {
        var userAccount = new UserAccount();
        userAccount.id = Objects.requireNonNull(id, "id");
        userAccount.email = Objects.requireNonNull(email, "email");
        userAccount.emailNormalized = Objects.requireNonNull(emailNormalized, "emailNormalized");
        userAccount.createdAt = Objects.requireNonNull(registrationTime, "registrationTime");
        userAccount.updatedAt = registrationTime;
        return userAccount;
    }
}
