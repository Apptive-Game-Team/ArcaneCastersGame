package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GameTimerPauseTest {
    @Test
    void repeatedPauseDoesNotResetTheFrozenTimeAndResumePreservesTheBudget() {
        Parameters parameters = mock(Parameters.class, RETURNS_DEEP_STUBS);
        when(parameters.object(GameObjectKey.GAME).longValue(ParameterKey.DURATION)).thenReturn(10000L);
        GameTimer timer = new GameTimer(parameters);
        ReflectionTestUtils.setField(timer, "endTime", 10000L);
        timer.setPaused(true, 1000);
        assertThat(timer.getRemainingTimeMillis()).isEqualTo(9000);
        timer.setPaused(true, 5000);
        assertThat(timer.getRemainingTimeMillis()).isEqualTo(9000);
        timer.setPaused(false, 6000);
        assertThat(ReflectionTestUtils.getField(timer, "endTime")).isEqualTo(15000L);
        timer.setPaused(false, 7000);
        assertThat(ReflectionTestUtils.getField(timer, "endTime")).isEqualTo(15000L);
        timer.setPaused(true, 8000);
        assertThat(timer.getRemainingTimeMillis()).isEqualTo(7000);
        timer.setPaused(false, 10000);
        assertThat(ReflectionTestUtils.getField(timer, "endTime")).isEqualTo(17000L);
    }
}
