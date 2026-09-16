package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.dto.frame.GameEventDto;

class GameContextEventsTest {

    private final GameContext gameContext = new GameContext(
            mock(GameTimer.class),
            mock(GameSessionData.class),
            mock(Parameters.class),
            mock(MagicInputHandler.class),
            mock(DatabaseMagicParser.class));

    @Test
    void drainsEventsOnceSoTheyAreNotResentEveryFrame() {
        gameContext.addEvent(GameEventDto.hit(1, 2));

        assertThat(gameContext.drainEvents()).containsExactly(GameEventDto.hit(1, 2));
        assertThat(gameContext.drainEvents()).isEmpty();
    }
}
