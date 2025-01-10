package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(name = "markets")
public class Market implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Country country;

    @NotEmpty
    @Column(nullable = false)
    private String region;

    @NotEmpty
    @Column(nullable = false, length = 10)
    private String currency;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Market() {
    }

    public Market(Country country, String region, String currency) {
        this.country = country;
        this.region = region;
        this.currency = currency;
    }
}
