package com.wordonline.server.game.domain;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.dto.Master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/**
 * The cooldown is a per-side {@link java.util.concurrent.atomic.AtomicLong} deadline rather than a
 * lock, so these pin the timing contract: a second emote inside the 3 second window is dropped, and
 * the side opens back up once the window has passed.
 */
class SessionObjectEmoteCooldownTest {

    private static final String SESSION_ID = "session-1";

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);

    @Test
    void secondEmoteInsideTheWindowIsDroppedAndTheThirdAfterItSendsAgain() {
        SessionObject sessionObject = sessionObject(11L, 22L);

        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.LeftPlayer)).isTrue();
        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.LeftPlayer)).isFalse();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> sessionObject.tryConsumeEmoteCooldown(Master.LeftPlayer));
    }

    @Test
    void cooldownIsTrackedSeparatelyPerSide() {
        SessionObject sessionObject = sessionObject(11L, 22L);

        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.LeftPlayer)).isTrue();
        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.RightPlayer)).isTrue();
        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.LeftPlayer)).isFalse();
        assertThat(sessionObject.tryConsumeEmoteCooldown(Master.RightPlayer)).isFalse();
    }

    private SessionObject sessionObject(long leftUserId, long rightUserId) {
        return new SessionObject(SESSION_ID, leftUserId, rightUserId, template,
                List.<Long>of(), List.<Long>of());
    }
}
