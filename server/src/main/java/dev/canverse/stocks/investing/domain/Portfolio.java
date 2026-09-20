package dev.canverse.stocks.investing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    public static final int MAX_NAME_LENGTH = 160;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_user_account_id", nullable = false, updatable = false)
    private UUID ownerUserAccountId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Portfolio create(UUID id, UUID ownerUserAccountId, String name, Instant observedAt) {
        var portfolio = new Portfolio();
        portfolio.id = Objects.requireNonNull(id, "id");
        portfolio.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        portfolio.name = displayName(name);
        portfolio.nameNormalized = normalizedName(portfolio.name);
        portfolio.createdAt = Objects.requireNonNull(observedAt, "observedAt");
        portfolio.updatedAt = observedAt;
        return portfolio;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void rename(String name, Instant observedAt) {
        requireActive();
        this.name = displayName(name);
        this.nameNormalized = normalizedName(this.name);
        this.updatedAt = nextUpdateTime(observedAt);
    }

    public void archive(Instant observedAt) {
        requireActive();
        archivedAt = Objects.requireNonNull(observedAt, "observedAt");
        updatedAt = nextUpdateTime(observedAt);
    }

    public static String displayName(String name) {
        var displayName = Objects.requireNonNull(name, "name").trim();
        if (displayName.isEmpty() || displayName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Portfolio name must contain 1 to 160 characters");
        }
        return displayName;
    }

    public static String normalizedName(String name) {
        var normalizedName = displayName(name).toUpperCase(Locale.ROOT);
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Normalized portfolio name must contain at most 160 characters");
        }
        return normalizedName;
    }

    public static List<UUID> normalizeAccountIds(List<UUID> accountIds) {
        Objects.requireNonNull(accountIds, "accountIds");
        var sortedIds = new TreeSet<UUID>();
        for (var accountId : accountIds) {
            if (!sortedIds.add(Objects.requireNonNull(accountId, "accountId"))) {
                throw new IllegalArgumentException("Portfolio account IDs must be unique");
            }
        }
        return List.copyOf(sortedIds);
    }

    private Instant nextUpdateTime(Instant observedAt) {
        var candidate = Objects.requireNonNull(observedAt, "observedAt").truncatedTo(ChronoUnit.MICROS);
        return candidate.isAfter(updatedAt) ? candidate : updatedAt.plus(1, ChronoUnit.MICROS);
    }

    private void requireActive() {
        if (isArchived()) {
            throw new IllegalStateException("An archived portfolio cannot be changed");
        }
    }
}
