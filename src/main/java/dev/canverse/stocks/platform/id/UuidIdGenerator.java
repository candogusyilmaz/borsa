package dev.canverse.stocks.platform.id;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UuidIdGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
