package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(name = "industries")
public class Industry implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Sector sector;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @Setter
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Industry() {
    }

    public Industry(Sector sector, String name) {
        this.sector = sector;
        this.name = name;
    }

    public Industry(Sector sector, String name, String description) {
        this(sector, name);
        this.description = description;
    }
}
