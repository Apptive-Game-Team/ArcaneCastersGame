package com.wordonline.server.game.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.magic.parser.MagicParser;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

class BotAgentReactionCadenceTest {

    // reaction_interval_frames counts 20-FPS frames: 8 of them is 0.4 s, 2.5 reactions a second.
    private static final BotPersona PERSONA = new BotPersona(
            1L, "cadence", BotTier.BEGINNER, 0, 8, 0.25, true, false, BotTemperament.WARM);

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void reactsAsOftenPerSecondAtAnyTickRate(int tickRate) {
        BotAgent agent = new BotAgent(mock(SessionObject.class), mock(MagicParser.class), Master.RightPlayer,
                PERSONA, mock(BotCounterEvaluator.class), 0.0);
        float deltaTime = 1f / tickRate;
        int reactions = 0;
        int firstReactionFrame = -1;

        // 8 s of game time, short of the 10 s periodic thought that would also answer true.
        for (int frame = 1; frame <= 8 * tickRate; frame++) {
            if (agent.shouldProcess(deltaTime)) {
                reactions++;
                if (firstReactionFrame < 0) {
                    firstReactionFrame = frame;
                }
            }
        }

        assertThat(reactions).isEqualTo(20);
        assertThat(firstReactionFrame / (double) tickRate).isEqualTo(0.4);
    }
}
