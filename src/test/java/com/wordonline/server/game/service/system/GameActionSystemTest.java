package com.wordonline.server.game.service.system;

import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameActionSystemTest {

    @Test
    void drainsQueuedActionsDuringItsUpdate() {
        GameContext gameContext = mock(GameContext.class);

        new GameActionSystem().update(gameContext);

        verify(gameContext).drainActions();
    }
}
