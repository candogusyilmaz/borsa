package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.domain.DeviceSession;
import dev.canverse.stocks.identity.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceSessionRotationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-15T12:00:00.125Z");
    private static final Instant ROTATED_AT = Instant.parse("2026-08-15T12:01:00.875Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-15T14:00:00.125Z");

    @Test
    void replacementRetainsFamilyOwnerLabelAndExpiryWithCleanLifecycle() {
        var user = user("10000000-0000-4000-8000-000000000001");
        var session = initialSession(user);

        var replacement = session.rotate(uuid("20000000-0000-4000-8000-000000000002"), "replacement-hash", ROTATED_AT);

        assertThat(replacement.getId()).isEqualTo(uuid("20000000-0000-4000-8000-000000000002"));
        assertThat(replacement.getUserAccount()).isSameAs(user);
        assertThat(replacement.getFamilyId()).isEqualTo(session.getFamilyId());
        assertThat(replacement.getDeviceLabel()).isEqualTo("phone");
        assertThat(replacement.getRefreshTokenHash()).isEqualTo("replacement-hash");
        assertThat(replacement.getCreatedAt()).isEqualTo(ROTATED_AT);
        assertThat(replacement.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(replacement.getLastUsedAt()).isNull();
        assertThat(replacement.getRevokedAt()).isNull();
        assertThat(replacement.getRevokeReason()).isNull();
        assertThat(replacement.getReplacedBySessionId()).isNull();
    }

    @Test
    void rotationConsumesPredecessorAndCannotBeRepeated() {
        var session = initialSession(user("30000000-0000-4000-8000-000000000003"));
        var replacementId = uuid("40000000-0000-4000-8000-000000000004");

        session.rotate(replacementId, "replacement-hash", ROTATED_AT);

        assertThat(session.getLastUsedAt()).isEqualTo(ROTATED_AT);
        assertThat(session.getRevokedAt()).isEqualTo(ROTATED_AT);
        assertThat(session.getRevokeReason()).isEqualTo(DeviceSession.ROTATED_REVOKE_REASON);
        assertThat(session.getReplacedBySessionId()).isEqualTo(replacementId);
        assertThatThrownBy(() -> session.rotate(uuid("50000000-0000-4000-8000-000000000005"), "second-hash", ROTATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reuseRevocationPreservesHistoricalRotationReason() {
        var session = initialSession(user("60000000-0000-4000-8000-000000000006"));
        var replacement = session.rotate(uuid("70000000-0000-4000-8000-000000000007"), "replacement-hash", ROTATED_AT);

        session.revokeForReuse(Instant.parse("2026-08-15T12:02:00Z"));
        replacement.revokeForReuse(Instant.parse("2026-08-15T12:02:00Z"));

        assertThat(session.getRevokeReason()).isEqualTo(DeviceSession.ROTATED_REVOKE_REASON);
        assertThat(replacement.getRevokeReason()).isEqualTo(DeviceSession.REUSE_DETECTED_REVOKE_REASON);
        assertThat(replacement.getRevokedAt()).isEqualTo(Instant.parse("2026-08-15T12:02:00Z"));
    }

    private DeviceSession initialSession(UserAccount user) {
        return DeviceSession.initialGeneration(uuid("80000000-0000-4000-8000-000000000008"), user, "initial-hash", "phone", CREATED_AT, EXPIRES_AT);
    }

    private UserAccount user(String id) {
        return UserAccount.register(uuid(id), "user@example.com", "user@example.com", CREATED_AT);
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
