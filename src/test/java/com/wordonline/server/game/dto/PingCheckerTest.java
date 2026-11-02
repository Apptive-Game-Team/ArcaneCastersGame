package com.wordonline.server.game.dto;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PingCheckerTest {

    private static final long USER_ID = 77L;

    @Test
    void timeoutIsPerInstanceWhenSessionsShareAUserId() {
        AtomicBoolean sessionATimedOut = new AtomicBoolean(false);
        PingChecker sessionA = new PingChecker(USER_ID, -1, userId -> sessionATimedOut.set(true), userId -> {
        });
        PingChecker sessionB = new PingChecker(USER_ID, -2, userId -> {
        }, userId -> {
        });

        sessionA.ping(USER_ID, 1);
        sessionB.ping(USER_ID, 1);

        await().atMost(Duration.ofSeconds(5)).untilTrue(sessionATimedOut);
        assertThat(sessionA.isDisconnected(USER_ID)).isTrue();
        sessionA.ping(USER_ID);
        assertThat(sessionA.isDisconnected(USER_ID)).isFalse();

        sessionA.close();
        sessionB.close();
    }

    @Test
    void closeCancelsPendingTimeout() throws InterruptedException {
        AtomicBoolean timedOut = new AtomicBoolean(false);
        PingChecker checker = new PingChecker(USER_ID, -1, userId -> timedOut.set(true), userId -> {
        });

        checker.ping(USER_ID, 1);
        checker.close();

        Thread.sleep(2000);
        assertThat(timedOut).isFalse();
    }
}
