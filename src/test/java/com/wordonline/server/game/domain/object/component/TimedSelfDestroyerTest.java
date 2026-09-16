package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.dto.frame.GaugeDto;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimedSelfDestroyerTest {

    private final GameObject gameObject = mock(GameObject.class);
    private final GameContext gameContext = mock(GameContext.class);

    private TimedSelfDestroyer destroyer(float timeToLive) {
        when(gameObject.getGameContext()).thenReturn(gameContext);
        return new TimedSelfDestroyer(gameObject, timeToLive);
    }

    // --- pre-existing behavior, pinned before freeze() is added ---

    @Test
    void doesNotDestroyBeforeItsTimeToLiveElapses() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update();

        verify(gameObject, never()).destroy();
    }

    @Test
    void destroysOnceElapsedTimeReachesTimeToLive() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update();
        destroyer.update();

        verify(gameObject).destroy();
    }

    @Test
    void recoverWithNoArgumentsResetsElapsedTimeToZero() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update();
        destroyer.recover();

        GaugeDto gauge = destroyer.getGauge();
        assertThat(gauge.value()).isEqualTo(3f);
        verify(gameObject).applyUpdate();
    }

    @Test
    void gaugeReportsRemainingTimeAgainstTimeToLiveAsTtlCategory() {
        when(gameContext.getDeltaTime()).thenReturn(2f);
        TimedSelfDestroyer destroyer = destroyer(5f);

        destroyer.update();

        GaugeDto gauge = destroyer.getGauge();
        assertThat(gauge.value()).isEqualTo(3f);
        assertThat(gauge.maxValue()).isEqualTo(5f);
        assertThat(gauge.category()).isEqualTo(GaugeCategory.TTL);
    }

    // --- new behavior: freeze() stops the next tick instead of rewinding elapsed time ---

    @Test
    void freezeSkipsTheNextTickWithoutAdvancingElapsedTime() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(10f);

        destroyer.update(); // elapsedTime = 1
        destroyer.freeze();
        destroyer.update(); // frozen: elapsedTime stays at 1

        assertThat(destroyer.getGauge().value()).isEqualTo(9f);
    }

    @Test
    void freezeNeverRewindsElapsedTimeEvenWhenSeveralSourcesFreezeTheSameFrame() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(10f);

        destroyer.update(); // elapsedTime = 1

        // three overlapping repair auras freeze the same object in one frame
        destroyer.freeze();
        destroyer.freeze();
        destroyer.freeze();
        destroyer.update();

        // one skipped tick, not three: elapsedTime is held at 1, never pushed below it
        assertThat(destroyer.getGauge().value()).isEqualTo(9f);
    }

    @Test
    void freezeHoldsForOneTickOnlySoDecayResumesWithoutIt() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(10f);

        destroyer.update(); // elapsedTime = 1
        destroyer.freeze();
        destroyer.update(); // frozen: elapsedTime = 1
        destroyer.update(); // not frozen any more: elapsedTime = 2

        assertThat(destroyer.getGauge().value()).isEqualTo(8f);
    }

    @Test
    void freezeEveryTickKeepsTheObjectAliveIndefinitely() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update(); // elapsedTime = 2, one tick short of destruction
        for (int i = 0; i < 100; i++) {
            destroyer.freeze();
            destroyer.update();
        }

        verify(gameObject, never()).destroy();
        assertThat(destroyer.getGauge().value()).isEqualTo(1f);
    }

    @Test
    void aFrozenTickCannotDestroyTheObject() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update(); // elapsedTime = 2
        destroyer.freeze();
        destroyer.update(); // the tick that would have destroyed it is skipped

        verify(gameObject, never()).destroy();
    }

    @Test
    void withoutAnyCallerOfFreezePlainDecayIsUnchanged() {
        when(gameContext.getDeltaTime()).thenReturn(1f);
        TimedSelfDestroyer destroyer = destroyer(3f);

        destroyer.update();
        destroyer.update();
        destroyer.update();

        verify(gameObject).destroy();
    }
}
