package com.wordonline.server.game.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs the database and lobby calls that follow a match end, so the loop thread that ended the
 * match does not wait on them. A round trip to the database costs about 190 ms from the US server,
 * and the loop thread used to make a dozen of them between the last frame and the result screen.
 *
 * <p>Virtual threads, because every task here waits on I/O and none paces a frame: the reason the
 * loops themselves run on platform threads (see SessionService.createSessionObject) does not apply.
 *
 * <p>A SmartLifecycle rather than a destroy method: lifecycle beans stop before any singleton is
 * destroyed, so the tasks still pending at shutdown drain while the DataSource and the lobby
 * client are alive. A task submitted after the drain started runs on the caller's thread instead
 * of being dropped, which is the old behaviour.
 */
@Slf4j
@Component(SessionTeardownExecutor.BEAN_NAME)
public class SessionTeardownExecutor implements Executor, SmartLifecycle {

    public static final String BEAN_NAME = "sessionTeardownExecutor";

    // docker stop sends SIGKILL ten seconds after SIGTERM by default, so waiting longer buys nothing.
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("session-teardown-", 0).factory());
    private volatile boolean running;

    @Override
    public void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("[SessionTeardown] Pending teardown tasks did not finish within {}", DRAIN_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SessionTeardown] Interrupted while draining pending teardown tasks");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
