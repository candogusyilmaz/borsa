package dev.canverse.stocks.domain.entity.portfolio;

import dev.canverse.stocks.domain.entity.account.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(schema = "portfolio", name = "portfolios")
public class Portfolio implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false)
    private String color;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private boolean archived = false;

    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY)
    private List<Position> positions = new ArrayList<>();

    protected Portfolio() {
    }

    public Portfolio(User user, String name, String color) {
        this.user = user;
        this.name = name;
        setColor(color);
    }

    public void archive() {
        this.archived = true;
    }

    public void setColor(String color) {
        var validColors = List.of(
                "#ef4444", // Red
                "#3b82f6", // Blue
                "#a855f7", // Purple
                "#10b981", // Emerald
                "#f59e0b", // Amber
                "#f43f5e", // Rose
                "#475569"  // Slate
        );

        if (!validColors.contains(color.toLowerCase())) {
            throw new IllegalArgumentException("Invalid color code.");
        }

        this.color = color;
    }
}
