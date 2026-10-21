package com.wordonline.server.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SessionTeardownExecutorTest {

    @Test
    void runsTasksOnAVirtualThread() throws Exception {
        SessionTeardownExecutor executor = new SessionTeardownExecutor();
        executor.start();
        CompletableFuture<Thread> ranOn = new CompletableFuture<>();

        executor.execute(() -> ranOn.complete(Thread.currentThread()));

        Thread thread = ranOn.get(5, TimeUnit.SECONDS);
        assertThat(thread.isVirtual()).isTrue();
        assertThat(thread.getName()).startsWith("session-teardown-");
        executor.stop();
    }

    // Spring stops lifecycle beans before it destroys the DataSource, so a teardown still writing
    // when the server shuts down finishes against a live database.
    @Test
    void stopWaitsForPendingTasks() throws Exception {
        SessionTeardownExecutor executor = new SessionTeardownExecutor();
        executor.start();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        executor.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        executor.stop();

        assertThat(finished).isTrue();
        assertThat(executor.isRunning()).isFalse();
    }

    @Test
    void aTaskSubmittedAfterStopRunsOnTheCallerInsteadOfBeingDropped() {
        SessionTeardownExecutor executor = new SessionTeardownExecutor();
        executor.start();
        executor.stop();
        AtomicReference<Thread> ranOn = new AtomicReference<>();

        executor.execute(() -> ranOn.set(Thread.currentThread()));

        assertThat(ranOn.get()).isSameAs(Thread.currentThread());
    }
}
