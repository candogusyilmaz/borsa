package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "currency", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency {

    @Id
    private String code;

    private String name;

    private String symbol;

    private short minorUnit;

    private boolean active;

    private Instant createdAt;
}
