package dev.canverse.stocks.testing;

import dev.canverse.stocks.platform.id.IdGenerator;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.UUID;

public final class RecordingIdGenerator implements IdGenerator {

    private final Deque<UUID> nextIds = new ArrayDeque<>();
    private final Deque<UUID> consumedIds = new ArrayDeque<>();
    private int invocations;

    public synchronized void setNextIds(UUID... ids) {
        nextIds.clear();
        consumedIds.clear();
        invocations = 0;
        nextIds.addAll(Arrays.asList(ids));
    }

    public synchronized void reset() {
        setNextIds();
    }

    public synchronized int remainingIds() {
        return nextIds.size();
    }

    public synchronized UUID peekNextId() {
        return nextIds.getFirst();
    }

    public synchronized int invocations() {
        return invocations;
    }

    public synchronized Deque<UUID> consumedIds() {
        return new ArrayDeque<>(consumedIds);
    }

    @Override
    public synchronized UUID next() {
        invocations++;
        var id = nextIds.isEmpty() ? UUID.randomUUID() : nextIds.removeFirst();
        consumedIds.addLast(id);
        return id;
    }
}
