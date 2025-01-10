package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(name = "stocks")
public class Stock implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false)
    private String symbol;

    @Column(unique = true, length = 12)
    private String isin;

    @ManyToOne(fetch = FetchType.LAZY)
    private Industry industry;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Country country;

    @Setter
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Stock() {
    }

    public Stock(String name, String symbol, String isin, Country country) {
        this.name = name;
        this.symbol = symbol;
        this.isin = isin;
        this.country = country;
    }

    public Stock(String name, String symbol, String isin, Country country, Industry industry, String description) {
        this(name, symbol, isin, country);
        this.industry = industry;
        this.description = description;
    }
}
