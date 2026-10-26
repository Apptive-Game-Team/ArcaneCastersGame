package com.wordonline.server.game.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class PracticeHoverPause {
    private static final long LEASE_MILLIS = 2000;
    private final Map<Long, Long> leases = new HashMap<>();
    private final LongSupplier clock;

    public PracticeHoverPause() {
        this(() -> System.nanoTime() / 1_000_000);
    }

    PracticeHoverPause(LongSupplier clock) {
        this.clock = clock;
    }

    public void setHovering(long userId, boolean hovering) {
        if (hovering) leases.put(userId, clock.getAsLong() + LEASE_MILLIS);
        else leases.remove(userId);
    }

    public boolean isPaused() {
        long now = clock.getAsLong();
        leases.values().removeIf(expires -> expires <= now);
        return !leases.isEmpty();
    }

    public void clear() {
        leases.clear();
    }
}
