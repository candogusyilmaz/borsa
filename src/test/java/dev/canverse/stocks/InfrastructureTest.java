package dev.canverse.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InfrastructureTest {

    @Test
    void clockCanBeFixedToDeterministicInstant() {
        var fixed = Instant.parse("2024-01-15T10:00:00Z");
        var clock = Clock.fixed(fixed, ZoneOffset.UTC);
        assertThat(Instant.now(clock)).isEqualTo(fixed);
    }

    @Test
    void idGeneratorFunctionalInterfaceSupportsLambdaOverride() {
        var ids = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        Iterator<UUID> iterator = ids.iterator();
        IdGenerator deterministic = iterator::next;

        assertThat(deterministic.next()).isEqualTo(ids.get(0));
        assertThat(deterministic.next()).isEqualTo(ids.get(1));
    }
}
