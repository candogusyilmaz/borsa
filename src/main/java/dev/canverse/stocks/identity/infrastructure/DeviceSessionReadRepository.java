package dev.canverse.stocks.identity.infrastructure;

import dev.canverse.stocks.identity.application.model.SessionCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceSessionReadRepository {

    private final JdbcClient jdbcClient;

    public List<DeviceSessionFamilyRecord> findFamilies(
            UUID userAccountId, UUID currentSessionId, SessionCursor cursor, int fetchLimit) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");

        if (cursor != null) {
            var sql = """
                    WITH family_summary AS (
                        SELECT
                            s.family_id,
                            MIN(s.created_at) AS created_at,
                            MAX(s.last_used_at) AS last_used_at,
                            MIN(s.expires_at) AS min_expires_at,
                            MAX(s.expires_at) AS max_expires_at,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN CAST(s.id AS text) END) AS latest_generation_id,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.device_label END) AS device_label,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoked_at END) AS terminal_revoked_at,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoke_reason END) AS terminal_revoke_reason,
                            BOOL_OR(s.id = :currentSessionId) AS is_current
                        FROM identity.device_session s
                        WHERE s.user_account_id = :userAccountId
                        GROUP BY s.family_id
                    )
                    SELECT *
                    FROM family_summary
                    WHERE (created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND family_id < :cursorFamilyId))
                    ORDER BY created_at DESC, family_id DESC
                    LIMIT :fetchLimit
                    """;
            return jdbcClient
                    .sql(sql)
                    .param("userAccountId", userAccountId)
                    .param("currentSessionId", currentSessionId)
                    .param("cursorCreatedAt", OffsetDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC))
                    .param("cursorFamilyId", cursor.familyId())
                    .param("fetchLimit", fetchLimit)
                    .query(this::mapFamilyRow)
                    .list();
        } else {
            var sql = """
                    WITH family_summary AS (
                        SELECT
                            s.family_id,
                            MIN(s.created_at) AS created_at,
                            MAX(s.last_used_at) AS last_used_at,
                            MIN(s.expires_at) AS min_expires_at,
                            MAX(s.expires_at) AS max_expires_at,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN CAST(s.id AS text) END) AS latest_generation_id,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.device_label END) AS device_label,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoked_at END) AS terminal_revoked_at,
                            MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoke_reason END) AS terminal_revoke_reason,
                            BOOL_OR(s.id = :currentSessionId) AS is_current
                        FROM identity.device_session s
                        WHERE s.user_account_id = :userAccountId
                        GROUP BY s.family_id
                    )
                    SELECT *
                    FROM family_summary
                    ORDER BY created_at DESC, family_id DESC
                    LIMIT :fetchLimit
                    """;
            return jdbcClient
                    .sql(sql)
                    .param("userAccountId", userAccountId)
                    .param("currentSessionId", currentSessionId)
                    .param("fetchLimit", fetchLimit)
                    .query(this::mapFamilyRow)
                    .list();
        }
    }

    public Optional<DeviceSessionFamilyRecord> findFamilyDetail(
            UUID userAccountId, UUID currentSessionId, UUID familyId) {
        Objects.requireNonNull(userAccountId, "userAccountId");
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        Objects.requireNonNull(familyId, "familyId");

        var sql = """
                SELECT
                    s.family_id,
                    MIN(s.created_at) AS created_at,
                    MAX(s.last_used_at) AS last_used_at,
                    MIN(s.expires_at) AS min_expires_at,
                    MAX(s.expires_at) AS max_expires_at,
                    MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN CAST(s.id AS text) END) AS latest_generation_id,
                    MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.device_label END) AS device_label,
                    MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoked_at END) AS terminal_revoked_at,
                    MAX(CASE WHEN s.replaced_by_session_id IS NULL THEN s.revoke_reason END) AS terminal_revoke_reason,
                    BOOL_OR(s.id = :currentSessionId) AS is_current
                FROM identity.device_session s
                WHERE s.user_account_id = :userAccountId AND s.family_id = :familyId
                GROUP BY s.family_id
                """;

        return jdbcClient
                .sql(sql)
                .param("userAccountId", userAccountId)
                .param("currentSessionId", currentSessionId)
                .param("familyId", familyId)
                .query(this::mapFamilyRow)
                .optional();
    }

    private DeviceSessionFamilyRecord mapFamilyRow(ResultSet rs, int rowNum) throws SQLException {
        var familyId = rs.getObject("family_id", UUID.class);
        var latestGenStr = rs.getString("latest_generation_id");
        var latestGenerationId = latestGenStr != null ? UUID.fromString(latestGenStr) : null;
        var deviceLabel = rs.getString("device_label");
        var createdAt = rs.getObject("created_at", OffsetDateTime.class);
        var lastUsedAt = rs.getObject("last_used_at", OffsetDateTime.class);
        var minExpiresAt = rs.getObject("min_expires_at", OffsetDateTime.class);
        var maxExpiresAt = rs.getObject("max_expires_at", OffsetDateTime.class);
        var terminalRevokedAt = rs.getObject("terminal_revoked_at", OffsetDateTime.class);
        var terminalRevokeReason = rs.getString("terminal_revoke_reason");
        var isCurrent = rs.getBoolean("is_current");

        return new DeviceSessionFamilyRecord(
                familyId,
                latestGenerationId,
                deviceLabel,
                createdAt != null ? createdAt.toInstant() : null,
                lastUsedAt != null ? lastUsedAt.toInstant() : null,
                minExpiresAt != null ? minExpiresAt.toInstant() : null,
                maxExpiresAt != null ? maxExpiresAt.toInstant() : null,
                terminalRevokedAt != null ? terminalRevokedAt.toInstant() : null,
                terminalRevokeReason,
                isCurrent);
    }
}
