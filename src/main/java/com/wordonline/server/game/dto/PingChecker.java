package com.wordonline.server.game.dto;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PingChecker {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int FIRST_PING_TIMEOUT_THRESHOLD = 30;
    private static final int PING_TIMEOUT_THRESHOLD = 10;

    private final Map<Long, ScheduledFuture<?>> pingTasks = new ConcurrentHashMap<>();
    private final Set<Long> nonPingUsers = ConcurrentHashMap.newKeySet();
    private final Consumer<Long> onNonPing;
    private final Consumer<Long> onPing;

    public PingChecker(long userId1, long userId2, Consumer<Long> onNonPing, Consumer<Long> onPing) {
        this.onNonPing = onNonPing;
        this.onPing = onPing;
        ping(userId1, FIRST_PING_TIMEOUT_THRESHOLD, false);
        ping(userId2, FIRST_PING_TIMEOUT_THRESHOLD, false);
    }

    public void ping(long userId) {
        ping(userId, PING_TIMEOUT_THRESHOLD);
    }

    public void ping(long userId, int threshold) {
        ping(userId, threshold, true);
    }

    private void ping(long userId, int threshold, boolean notifyRecovery) {
        if (userId < 0) return;

        if (notifyRecovery && nonPingUsers.remove(userId)) {
            log.trace("Ping recovered: userId={}", userId);
            onPing.accept(userId);
        }

        ScheduledFuture<?> existing = pingTasks.get(userId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            log.trace("No ping for {} seconds: userId={}", threshold, userId);
            pingTasks.remove(userId);
            nonPingUsers.add(userId);
            onNonPing.accept(userId);
        }, threshold, TimeUnit.SECONDS);

        pingTasks.put(userId, task);

    }

    // cancels every pending timeout owned by this session so they cannot outlive the match
    public boolean isDisconnected(long userId) {
        return nonPingUsers.contains(userId);
    }

    public void close() {
        pingTasks.values().forEach(task -> task.cancel(false));
        pingTasks.clear();
        nonPingUsers.clear();
    }
}
