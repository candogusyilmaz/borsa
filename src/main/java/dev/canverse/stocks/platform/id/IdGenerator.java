package dev.canverse.stocks.platform.id;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID next();
}
