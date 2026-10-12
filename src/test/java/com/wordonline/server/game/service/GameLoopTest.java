package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.Parameters;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * is_running() feeds the admin room list, the lobby readiness check and the bot auto-match
 * top-up, so these cover the two ways it used to lie: before the thread starts, and after the
 * thread dies on a Throwable the frame catch block does not handle.
 */
class GameLoopTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static GameLoop loopThat(Runnable body) {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getTickRate()).thenReturn(20);
        return new GameLoop(mock(MmrService.class), mock(UserService.class),
                gameContext, mock(Parameters.class)) {
            @Override
            void update() {
                body.run();
            }
        };
    }

    @Test
    void isNotRunningUntilTheThreadTicks() {
        GameLoop loop = loopThat(() -> {
        });

        assertThat(loop.is_running()).isFalse();
        assertThat(loop.awaitStart(Duration.ofMillis(100))).isFalse();
    }

    @Test
    void reportsRunningOnceStartedAndStopsReportingAfterClose() throws Exception {
        GameLoop loop = loopThat(() -> {
        });

        Thread thread = new Thread(loop);
        thread.start();

        assertThat(loop.awaitStart(TIMEOUT)).isTrue();
        assertThat(loop.is_running()).isTrue();

        loop.close();
        thread.join(TIMEOUT.toMillis());

        assertThat(loop.is_running()).isFalse();
    }

    @Test
    void stopsReportingRunningWhenTheFrameThrowsAnError() {
        AtomicBoolean ticked = new AtomicBoolean(false);
        GameLoop loop = loopThat(() -> {
            ticked.set(true);
            throw new StackOverflowError("frame blew the stack");
        });

        Thread thread = new Thread(loop);
        thread.setUncaughtExceptionHandler((t, e) -> {
        });
        thread.start();

        await().atMost(TIMEOUT).until(ticked::get);
        await().atMost(TIMEOUT).until(() -> !loop.is_running());
    }

    @Test
    void awaitStartReturnsFalseOnceTheLoopHasAlreadyTerminated() throws Exception {
        GameLoop loop = loopThat(() -> {
        });
        loop.close();

        Thread thread = new Thread(loop);
        thread.start();
        thread.join(TIMEOUT.toMillis());

        assertThat(loop.awaitStart(Duration.ofMillis(100))).isFalse();
    }
}
