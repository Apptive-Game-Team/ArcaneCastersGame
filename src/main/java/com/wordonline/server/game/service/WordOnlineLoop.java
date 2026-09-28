package com.wordonline.server.game.service;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.bot.BotAgent;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.system.BotAgentSystem;
import com.wordonline.server.game.service.system.ComponentUpdateSystem;
import com.wordonline.server.game.service.system.FeverTimeSystem;
import com.wordonline.server.game.service.system.GameActionSystem;
import com.wordonline.server.game.service.system.GameObjectAddRemoteSystem;
import com.wordonline.server.game.service.system.GameObjectStateInitialSystem;
import com.wordonline.server.game.service.system.PhysicSystem;
import com.wordonline.server.game.service.system.SyncFrameDataSystem;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@Scope("prototype")
@Service
public class WordOnlineLoop extends GameLoop {

    private final SyncFrameDataSystem frameDataSystem;
    private final GameActionSystem gameActionSystem;
    private final BotAgentSystem botSystem;
    private final FeverTimeSystem feverTimeSystem;
    private final GameObjectStateInitialSystem gameObjectStateInitialSystem;
    private final ComponentUpdateSystem componentUpdateSystem;
    private final PhysicSystem physicSystem;
    private final GameObjectAddRemoteSystem gameObjectAddRemoveSystem;
    private final DatabaseMagicParser magicParser;
    private final BotPersonaService botPersonaService;
    private final BotCounterEvaluator botCounterEvaluator;

    private volatile BotAgent leftBotAgent;
    private volatile BotAgent rightBotAgent;

    public WordOnlineLoop(MmrService mmrService,
                          UserService userService, GameContext gameContext,
                          Parameters parameters, SyncFrameDataSystem frameDataSystem,
                          GameActionSystem gameActionSystem, BotAgentSystem botSystem,
            FeverTimeSystem feverTimeSystem,
                          GameObjectStateInitialSystem gameObjectStateInitialSystem,
                          ComponentUpdateSystem componentUpdateSystem, PhysicSystem physicSystem,
                          GameObjectAddRemoteSystem gameObjectAddRemoveSystem, DatabaseMagicParser magicParser,
                          BotPersonaService botPersonaService, BotCounterEvaluator botCounterEvaluator) {
        super(mmrService, userService, gameContext, parameters);
        this.frameDataSystem = frameDataSystem;
        this.gameActionSystem = gameActionSystem;
        this.botSystem = botSystem;
        this.feverTimeSystem = feverTimeSystem;
        this.gameObjectStateInitialSystem = gameObjectStateInitialSystem;
        this.componentUpdateSystem = componentUpdateSystem;
        this.physicSystem = physicSystem;
        this.gameObjectAddRemoveSystem = gameObjectAddRemoveSystem;
        this.magicParser = magicParser;
        this.botPersonaService = botPersonaService;
        this.botCounterEvaluator = botCounterEvaluator;
    }

    @Override
    public void init(SessionObject sessionObject, Runnable onTerminated) {
        gameContext.init(sessionObject, this);
        super.init(sessionObject, onTerminated);
        if(sessionObject.getSessionType() == SessionType.Practice) {
            initializeBotAgents(sessionObject);
        }
    }

    private void initializeBotAgents(SessionObject sessionObject) {
        if(sessionObject.isLeftBot()) {
            leftBotAgent = newBotAgent(sessionObject, Master.LeftPlayer, sessionObject.getLeftUserId());
        }
        if(sessionObject.isRightBot()) {
            rightBotAgent = newBotAgent(sessionObject, Master.RightPlayer, sessionObject.getRightUserId());
        }
    }

    private BotAgent newBotAgent(SessionObject sessionObject, Master side, long participantId) {
        return new BotAgent(
                sessionObject,
                magicParser,
                side,
                botPersonaService.findByParticipantIdOrDefault(participantId),
                botCounterEvaluator,
                opponentNoviceProgress(sessionObject, side)
        );
    }

    /**
     * How far the human on the other side is through the tutorial. The hospitality bot holds back
     * by exactly this much, so it is read per session rather than stored on the persona - the same
     * bot faces players at different points and must not treat them alike.
     */
    private double opponentNoviceProgress(SessionObject sessionObject, Master botSide) {
        long opponentId = botSide == Master.LeftPlayer
                ? sessionObject.getRightUserId()
                : sessionObject.getLeftUserId();
        return getUserService().noviceProgress(opponentId);
    }

    /** The bot playing that side, or {@code null} when a human has it. */
    public BotAgent getBotAgent(Master side) {
        if (side == Master.LeftPlayer) {
            return leftBotAgent;
        } else if (side == Master.RightPlayer) {
            return rightBotAgent;
        } else {
            return null;
        }
    }

    // Both bot toggles run on the loop thread: the ping timeout scheduler queues them on the game
    // action queue rather than swapping the agent out from under a frame.
    public void activateBotForUser(long userId) {
        Master side = sessionObject.getUserSide(userId);
        if (side == Master.LeftPlayer) {
            activateLeftBot();
        } else if (side == Master.RightPlayer) {
            activateRightBot();
        }
    }

    private void activateLeftBot() {
        if (leftBotAgent != null) {
            return;
        }
        leftBotAgent = newBotAgent(sessionObject, Master.LeftPlayer, sessionObject.getLeftUserId());
        log.info("Activated bot control for disconnected user: side={}", Master.LeftPlayer);
    }

    private void activateRightBot() {
        if (rightBotAgent != null) {
            return;
        }
        rightBotAgent = newBotAgent(sessionObject, Master.RightPlayer, sessionObject.getRightUserId());
        log.info("Activated bot control for disconnected user: side={}", Master.RightPlayer);
    }

    public void deactivateBotForUser(long userId) {
        Master side = sessionObject.getUserSide(userId);
        if (side == Master.LeftPlayer) {
            deactivateLeftBot();
        } else if (side == Master.RightPlayer) {
            deactivateRightBot();
        }
    }

    private void deactivateLeftBot() {
        if (sessionObject.isLeftBot() || leftBotAgent == null) {
            return;
        }
        leftBotAgent = null;
        log.info("Deactivated bot control for reconnected user: side={}", Master.LeftPlayer);
    }

    private void deactivateRightBot() {
        if (sessionObject.isRightBot() || rightBotAgent == null) {
            return;
        }
        rightBotAgent = null;
        log.info("Deactivated bot control for reconnected user: side={}", Master.RightPlayer);
    }

    protected void update() {
        // External producers enqueue only. This first system phase applies their work on the loop
        // thread before any system reads or mutates the frame's game state.
        gameActionSystem.update(gameContext);

        // Initial DTOs
        frameDataSystem.earlyUpdate(gameContext);

        feverTimeSystem.update(gameContext);

        // bot tick (practice bots and pve enemy bots are both handled inside BotAgentSystem)
        botSystem.update(gameContext);

        beforeResultCheck();

        if (gameContext.getGameTimer().isEnd()) {
            resolveTimedOutMatch();
            gameContext.getResultChecker().setEnd();
        }

        // Check for game over
        if (gameContext.getResultChecker().checkResult()) {
            handleGameEnd();
        }

        gameObjectStateInitialSystem.update(gameContext);

        // Run GameObject's Updates
        componentUpdateSystem.update(gameContext);

        physicSystem.update(gameContext);

        gameObjectAddRemoveSystem.update(gameContext);

        // The snapshot is only read by the sync frame, so it is only built on one. frameNum is
        // incremented once at the top of the frame, so this and SyncFrameDataSystem agree.
        if (isSyncFrame(gameContext.getFrameNum())) {
            buildSnapshot();
        }

        frameDataSystem.lateUpdate(gameContext);
    }

    // Timed-out matches need a concrete result so rating/stat pipelines can record the match.
    private void resolveTimedOutMatch() {
        if (gameContext.getResultChecker().getLoser() != null) {
            return;
        }

        int leftHp = gameContext.getGameSessionData().leftPlayerData.hp;
        int rightHp = gameContext.getGameSessionData().rightPlayerData.hp;
        if (leftHp == rightHp) {
            return;
        }

        Master loser = leftHp < rightHp ? Master.LeftPlayer : Master.RightPlayer;
        gameContext.getResultChecker().setLoser(loser);
        log.info("[GameResult] resolved timed-out match by hp: leftHp={}, rightHp={}, loser={}",
                leftHp, rightHp, loser);
    }

    protected void beforeResultCheck() {
        // hook for specialized loops (e.g. PVE)
    }
}
