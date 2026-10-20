package com.wordonline.server.game.service;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.llm.BotDecisionSourceFactory;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.BotAgentSystem;
import com.wordonline.server.game.service.system.ComponentUpdateSystem;
import com.wordonline.server.game.service.system.FeverTimeSystem;
import com.wordonline.server.game.service.system.GameActionSystem;
import com.wordonline.server.game.service.system.GameObjectAddRemoteSystem;
import com.wordonline.server.game.service.system.GameObjectStateInitialSystem;
import com.wordonline.server.game.service.system.PhysicSystem;
import com.wordonline.server.game.service.system.SyncFrameDataSystem;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WordOnlineLoopBotTakeoverTest {

    private static final long HUMAN_ID = 5L;
    private static final long BOT_ID = -9L;

    private final UserService userService = mock(UserService.class);
    private final BotPersonaService botPersonaService = mock(BotPersonaService.class);

    @Test
    void takeoverAfterInitMakesNoRepositoryCall() {
        BotPersona hospitality = new BotPersona(BOT_ID, "Host", BotTier.HOSPITALITY, 1200, 30, -1.0, true, true,
                BotTemperament.WARM);
        when(botPersonaService.findByParticipantIdOrDefault(BOT_ID)).thenReturn(hospitality);
        when(userService.noviceProgress(HUMAN_ID)).thenReturn(0.7);
        when(userService.noviceProgress(BOT_ID)).thenReturn(1.0);

        SessionObject session = new SessionObject("takeover", HUMAN_ID, BOT_ID, mock(SimpMessagingTemplate.class),
                List.of(), List.of(), SessionType.Practice, null, com.wordonline.server.game.domain.map.GameMap.GRASSLAND);
        session.setGameLoop(mock(GameLoop.class));
        WordOnlineLoop loop = loop();
        loop.sessionObject = session;
        loop.loadBotTakeoverInfo(session);

        assertThat(session.getBotPersona(BOT_ID)).isSameAs(hospitality);
        assertThat(session.getBotPersona(HUMAN_ID)).isSameAs(BotPersona.DEFAULT);
        assertThat(session.getNoviceProgress(HUMAN_ID)).isEqualTo(0.7);

        org.mockito.Mockito.clearInvocations(userService, botPersonaService);
        loop.activateBotForUser(HUMAN_ID);

        assertThat(loop.getBotAgent(Master.LeftPlayer)).isNotNull();
        verifyNoMoreInteractions(userService, botPersonaService);
        verify(userService, never()).noviceProgress(anyLong());
    }

    private WordOnlineLoop loop() {
        return new WordOnlineLoop(mock(MmrService.class), userService, mock(GameContext.class),
                mock(Parameters.class), mock(SyncFrameDataSystem.class), mock(GameActionSystem.class),
                mock(BotAgentSystem.class), mock(FeverTimeSystem.class), mock(GameObjectStateInitialSystem.class),
                mock(ComponentUpdateSystem.class), mock(PhysicSystem.class),
                mock(GameObjectAddRemoteSystem.class), mock(DatabaseMagicParser.class),
                botPersonaService, mock(BotCounterEvaluator.class), BotDecisionSourceFactory.ruleOnly());
    }
}
