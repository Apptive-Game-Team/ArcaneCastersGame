package com.wordonline.server.game.service;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import com.wordonline.server.game.service.pve.PveScenarioRegistry;
import com.wordonline.server.game.service.system.PveScriptSystem;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Scope("prototype")
public class PveLoop extends WordOnlineLoop {

    private static final Long DEFAULT_SCENARIO_ID = 1L;

    private final PveScenarioRegistry pveScenarioRegistry;
    private final PveScenarioInstaller pveScenarioInstaller;
    private final PveScriptSystem pveScriptSystem;

    public PveLoop(MmrService mmrService,
                   UserService userService,
                   GameContext gameContext,
                   com.wordonline.server.game.domain.Parameters parameters,
                   com.wordonline.server.game.service.system.SyncFrameDataSystem frameDataSystem,
                   com.wordonline.server.game.service.system.GameActionSystem gameActionSystem,
                   com.wordonline.server.game.service.system.BotAgentSystem botSystem,
                   com.wordonline.server.game.service.system.FeverTimeSystem feverTimeSystem,
                   com.wordonline.server.game.service.system.GameObjectStateInitialSystem gameObjectStateInitialSystem,
                   com.wordonline.server.game.service.system.ComponentUpdateSystem componentUpdateSystem,
                   com.wordonline.server.game.service.system.PhysicSystem physicSystem,
                   com.wordonline.server.game.service.system.GameObjectAddRemoteSystem gameObjectAddRemoveSystem,
                   com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser magicParser,
                   BotPersonaService botPersonaService,
                   BotCounterEvaluator botCounterEvaluator,
                   PveScenarioRegistry pveScenarioRegistry,
                   PveScenarioInstaller pveScenarioInstaller,
                   PveScriptSystem pveScriptSystem) {
        super(mmrService, userService, gameContext, parameters, frameDataSystem, gameActionSystem, botSystem, feverTimeSystem,
                gameObjectStateInitialSystem, componentUpdateSystem, physicSystem, gameObjectAddRemoveSystem, magicParser,
                botPersonaService, botCounterEvaluator);
        this.pveScenarioRegistry = pveScenarioRegistry;
        this.pveScenarioInstaller = pveScenarioInstaller;
        this.pveScriptSystem = pveScriptSystem;
    }

    @Override
    public void init(SessionObject sessionObject, Runnable onTerminated) {
        gameContext.init(sessionObject, this);
        initializeLoop(sessionObject, onTerminated, false);

        PveResultChecker resultChecker = new PveResultChecker(sessionObject);
        gameContext.setResultChecker(resultChecker);

        // PveLoop should always setup a PVE scenario regardless of SessionType guard.
        setupPveScenario(sessionObject, resultChecker);
    }

    @Override
    protected void beforeResultCheck() {
        if (sessionObject.getSessionType() != SessionType.PVE) {
            return;
        }

        pveScriptSystem.update(gameContext);
    }

    private void setupPveScenario(SessionObject sessionObject, PveResultChecker resultChecker) {
        Long scenarioId = resolveScenarioId(sessionObject.getScenarioId());
        var scenario = pveScenarioRegistry.getScenario(scenarioId);

        pveScenarioInstaller.install(scenario.installers(), gameContext);
        pveScriptSystem.setScenario(scenario);
        pveScriptSystem.setRuntime(pveScenarioInstaller.getRuntime());

        List<Integer> objectiveIds = scenario.objectiveInstallerIds().stream()
                .map(installerId -> pveScenarioInstaller.getRuntime() == null
                        ? -1
                        : pveScenarioInstaller.getRuntime().getInstalledObjectId(installerId))
                .toList();
        resultChecker.setObjectiveIds(objectiveIds);
    }

    private Long resolveScenarioId(Long scenarioId) {
        if (scenarioId == null) {
            return DEFAULT_SCENARIO_ID;
        }
        return scenarioId;
    }

    @Override
    protected void handleGameEnd() {
        long leftId = sessionObject.getLeftUserId();
        long rightId = sessionObject.getRightUserId();

        ResultMmrDto mmrDto = new ResultMmrDto((short) 0, (short) 0, (short) 0, (short) 0);
        gameContext.getResultChecker().broadcastResult(mmrDto);

        if (leftId >= 0) {
            getUserService().markOnline(leftId);
        }
        if (rightId >= 0) {
            getUserService().markOnline(rightId);
        }
        close();
    }
}
