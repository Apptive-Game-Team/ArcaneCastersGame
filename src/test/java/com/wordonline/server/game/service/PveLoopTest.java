package com.wordonline.server.game.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import com.wordonline.server.game.service.pve.PveScenarioRegistry;
import com.wordonline.server.game.service.system.BotAgentSystem;
import com.wordonline.server.game.service.system.ComponentUpdateSystem;
import com.wordonline.server.game.service.system.FeverTimeSystem;
import com.wordonline.server.game.service.system.GameActionSystem;
import com.wordonline.server.game.service.system.GameObjectAddRemoteSystem;
import com.wordonline.server.game.service.system.GameObjectStateInitialSystem;
import com.wordonline.server.game.service.system.PhysicSystem;
import com.wordonline.server.game.service.system.PveObjectiveSystem;
import com.wordonline.server.game.service.system.PveScriptSystem;
import com.wordonline.server.game.service.system.SyncFrameDataSystem;

class PveLoopTest {

    private final GameContext gameContext = mock(GameContext.class);
    private final PveScriptSystem scriptSystem = mock(PveScriptSystem.class);
    private final PveObjectiveSystem objectiveSystem = mock(PveObjectiveSystem.class);
    private final PveLoop loop = new PveLoop(
            mock(MmrService.class), mock(UserService.class), gameContext, mock(Parameters.class),
            mock(SyncFrameDataSystem.class), mock(GameActionSystem.class), mock(BotAgentSystem.class),
            mock(FeverTimeSystem.class), mock(GameObjectStateInitialSystem.class),
            mock(ComponentUpdateSystem.class), mock(PhysicSystem.class), mock(GameObjectAddRemoteSystem.class),
            mock(DatabaseMagicParser.class), mock(BotPersonaService.class), mock(BotCounterEvaluator.class),
            mock(PveScenarioRegistry.class), mock(PveScenarioInstaller.class), scriptSystem);

    @Test
    void sendPveStateToSendsTheObjectiveThenTheMissedEventsThenTheStatesToTheAskingUser() {
        ReflectionTestUtils.setField(loop, "pveObjectiveSystem", objectiveSystem);

        loop.sendPveStateTo(7L, 3);

        InOrder order = inOrder(objectiveSystem, scriptSystem);
        order.verify(objectiveSystem).sendCurrentTo(gameContext, 7L);
        order.verify(scriptSystem).sendRecentEventsTo(gameContext, 7L, 3);
        order.verify(scriptSystem).sendStatesTo(gameContext, 7L);
    }

    @Test
    void sendPveStateToStillReplaysEventsBeforeTheObjectiveSystemExists() {
        loop.sendPveStateTo(7L, 0);

        org.mockito.Mockito.verify(scriptSystem).sendRecentEventsTo(gameContext, 7L, 0);
    }
}
