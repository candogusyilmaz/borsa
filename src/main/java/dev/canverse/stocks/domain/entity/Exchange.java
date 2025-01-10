package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(name = "exchanges")
public class Exchange implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Country country;

    @NotEmpty
    @Column(nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Exchange() {
    }

    public Exchange(String name, String code, Country country, String timezone) {
        this.name = name;
        this.code = code;
        this.country = country;
        this.timezone = timezone;
    }
}
