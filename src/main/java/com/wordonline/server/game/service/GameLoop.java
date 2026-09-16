package com.wordonline.server.game.service;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.*;
import com.wordonline.server.game.dto.frame.SnapshotObjectDto;
import com.wordonline.server.game.dto.frame.SnapshotResponseDto;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.dto.result.ResultType;
import com.wordonline.server.game.util.*;
import com.wordonline.server.websocket.SpectatorSubscriptionRegistry;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// GameLoop is the main class that runs the game loop
@Slf4j
@RequiredArgsConstructor
public abstract class GameLoop implements Runnable {

    private enum LoopState { CREATED, RUNNING, TERMINATED }

    // The admin room list, the lobby readiness check and the bot scheduler all read is_running(),
    // so it has to mean "the loop thread is ticking" rather than "a GameLoop object exists".
    // RUNNING is set from inside the loop thread and cleared in a finally block, so neither the
    // window before Thread.start() takes effect nor a thread dying on an Error can leave a session
    // advertising itself as alive.
    private volatile LoopState state = LoopState.CREATED;
    private volatile boolean stopRequested = false;
    private final CountDownLatch startSignal = new CountDownLatch(1);

    // The thread ticking this loop, captured on entry so the watchdog can interrupt a
    // stalled loop and snapshot where it is stuck. close() alone cannot end a loop whose
    // thread never returns to the while condition.
    private volatile Thread loopThread;

    // Written by the loop thread once per completed frame and read by monitoring threads.
    // deltaTime cannot serve that purpose: it holds the last completed frame's duration, so a
    // loop thread that dies mid-frame keeps reporting a plausible fps forever. The age of this
    // timestamp is the one signal that keeps moving when the loop does not.
    @Getter
    private volatile long lastFrameEndMillis = System.currentTimeMillis();

    public static final int FPS = 20;

    // Every tenth frame SyncFrameDataSystem replaces the frame message with a full snapshot.
    // The loop builds the snapshot for it, so both sides read the period from here rather than
    // from two literals that can drift apart.
    public static final int SYNC_FRAME_INTERVAL = 10;

    public static boolean isSyncFrame(int frameNum) {
        return frameNum % SYNC_FRAME_INTERVAL == 0;
    }

    public SessionObject sessionObject;

    // Spectator subscriptions are counted for the whole application, so the registry is a
    // singleton the loop hands to its session. Setter injection rather than a constructor
    // argument: GameLoop's constructor is chained through WordOnlineLoop into PveLoop, and this
    // dependency belongs to none of them.
    private SpectatorSubscriptionRegistry spectatorSubscriptionRegistry;
    private Runnable onTerminated;

    private final MmrService mmrService;
    private final UserService userService;

    protected MmrService getMmrService() {
        return mmrService;
    }

    protected UserService getUserService() {
        return userService;
    }

    @Getter
    protected final GameContext gameContext;

    public final Parameters parameters;

    @Getter
    protected volatile List<SnapshotObjectDto> lastSnapshotObjects = List.of();
    protected volatile int lastSnapshotFrameNum = 0;

    public void init(SessionObject sessionObject, Runnable onTerminated) {
        initializeLoop(sessionObject, onTerminated, true);
    }

    @Autowired
    public void setSpectatorSubscriptionRegistry(SpectatorSubscriptionRegistry spectatorSubscriptionRegistry) {
        this.spectatorSubscriptionRegistry = spectatorSubscriptionRegistry;
    }

    protected final void initializeLoop(SessionObject sessionObject, Runnable onTerminated, boolean createRightPlayer) {
        this.sessionObject = sessionObject;
        this.onTerminated = onTerminated;
        // Every loop, PVP and PVE alike, reaches this method, so this is the one place the session
        // learns how to tell whether a spectator is listening.
        sessionObject.setSpectatorSubscriptionRegistry(spectatorSubscriptionRegistry);

        new GameObject(Master.LeftPlayer, PrefabType.Player, GameConfig.LEFT_PLAYER_POSITION, gameContext);
        if (createRightPlayer) {
            new GameObject(Master.RightPlayer, PrefabType.Player, GameConfig.RIGHT_PLAYER_POSITION, gameContext);
        }
        new GameObject(Master.None, PrefabType.Wall, Vector3.ZERO, gameContext);
    }

    public boolean is_running() {
        return state == LoopState.RUNNING;
    }

    // Lets the creator wait for the first tick instead of reporting a session ready the moment the
    // object exists. Returns false on timeout; the caller decides whether that is fatal.
    public boolean awaitStart(Duration timeout) {
        try {
            return startSignal.await(timeout.toMillis(), TimeUnit.MILLISECONDS) && is_running();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void close() {
        stopRequested = true;
    }

    // Best effort: wakes a thread parked in sleep/wait, but cannot break a synchronized
    // wait or a runaway loop - callers must not assume the thread actually dies.
    public void interruptLoopThread() {
        Thread thread = loopThread;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    // Where the loop thread is right now; the watchdog records this as the primary
    // evidence of what a stalled loop was blocked on. Empty when the thread is gone.
    public StackTraceElement[] captureLoopThreadStackTrace() {
        Thread thread = loopThread;
        if (thread == null || !thread.isAlive()) {
            return new StackTraceElement[0];
        }
        return thread.getStackTrace();
    }

    @Override
    public void run() {
        runLoop();
    }

    // this method is called when the game loop is started
    private void runLoop() {
        long frameDuration = 1000 / FPS;

        loopThread = Thread.currentThread();
        state = LoopState.RUNNING;
        startSignal.countDown();

        try {
            while (!stopRequested) {
                gameContext.incrementFrameNum();
                long startTime = System.currentTimeMillis();

                try {
                    // Every write to game state happens on this thread. update() runs
                    // GameActionSystem first, applying the input, bot and ping actions other
                    // threads queued since the last frame, and nothing else mutates game state,
                    // so no lock is taken here or anywhere below.
                    update();
                } catch (Exception e) {
                    log.error("[ERROR] {}", e.getMessage(), e);
                    finalizeAfterFailure();
                    break;
                }

                long endTime = System.currentTimeMillis();
                long sleepTime = frameDuration - (endTime - startTime);
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {
                    }
                }
                gameContext.setDeltaTime((System.currentTimeMillis() - startTime) / 1000.0f);
                lastFrameEndMillis = System.currentTimeMillis();
            }
        } finally {
            // An Error thrown out of update() escapes the catch above. Without this the thread would
            // die while the session stayed in the registry, permanently reported as active.
            state = LoopState.TERMINATED;
            startSignal.countDown();

            if (onTerminated != null) {
                try {
                    onTerminated.run();
                } catch (Exception e) {
                    log.warn("onTerminated failed", e);
                }
            }
        }
    }

    // A crashed frame must still deliver a result and release both users from the in-game state.
    // handleGameEnd() calls close(), so an unset stopRequested means the match was not ended yet.
    private void finalizeAfterFailure() {
        if (stopRequested) {
            return;
        }
        try {
            gameContext.getResultChecker().setEnd();
            handleGameEnd();
        } catch (Exception e) {
            log.error("failed to finalize match after loop failure", e);
        }
    }

    // this method is called when the game loop is stopped
    abstract void update();

    protected void handleGameEnd() {
        Master loser = gameContext.getResultChecker().getLoser();

        long leftId = sessionObject.getLeftUserId();
        long rightId = sessionObject.getRightUserId();
        ResultType outcomeLeft;
        if (loser == Master.LeftPlayer) {
            outcomeLeft = ResultType.Lose;
        } else if (loser == Master.RightPlayer) {
            outcomeLeft = ResultType.Win;
        } else {
            outcomeLeft = ResultType.Draw;
        }

        short leftMmr = mmrService.fetchRating(leftId);
        short rightMmr = mmrService.fetchRating(rightId);
        ResultMmrDto mmrDto = new ResultMmrDto(leftMmr, rightMmr, leftMmr, rightMmr);

        if (sessionObject.getSessionType() == SessionType.PVP || sessionObject.getSessionType() == SessionType.Practice) {
            mmrDto = mmrService.updateMatchResult(leftId, rightId, outcomeLeft);
        }
        gameContext.getResultChecker().broadcastResult(mmrDto);

        userService.markOnline(leftId);
        userService.markOnline(rightId);

        // 3) Loop end
        close();
    }

    // 2) Build snapshot. Only called on sync frames: nothing else reads the result.
    protected void buildSnapshot() {
        lastSnapshotObjects = gameContext.getGameObjects()
                .stream()
                .map(SnapshotMapper::toDto)
                .toList();
        lastSnapshotFrameNum = gameContext.getFrameNum();
    }

    public SnapshotResponseDto getLastSnapshot(Long userId) {
        List<Long> cards;
        if (gameContext.getSessionObject().getLeftUserId() == userId) {
            cards = gameContext.getGameSessionData().leftPlayerData.cards;
        } else if (gameContext.getSessionObject().getRightUserId() == userId) {
            cards = gameContext.getGameSessionData().rightPlayerData.cards;
        } else {
            cards = List.of();
        }
        return new SnapshotResponseDto(lastSnapshotFrameNum, lastSnapshotObjects, cards);
    }
}
