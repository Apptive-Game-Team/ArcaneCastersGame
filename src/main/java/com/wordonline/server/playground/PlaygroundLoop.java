package com.wordonline.server.playground;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.*;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;

@Service
@Scope("prototype")
@ConditionalOnProperty(name = "playground.enabled", havingValue = "true")
public class PlaygroundLoop extends WordOnlineLoop {
    public static final int PLAYER_HP = 99_999_999;
    public static final Duration LIFETIME = Duration.ofSeconds(300);
    private final Clock clock;
    private Instant expiresAt;
    private boolean leftImmune;
    private boolean rightImmune;
    private volatile boolean ended;

    public PlaygroundLoop(MmrService mmr, UserService users, GameContext context, Parameters parameters,
            SyncFrameDataSystem frames, GameActionSystem actions, BotAgentSystem bots, FeverTimeSystem fever,
            GameObjectStateInitialSystem initial, ComponentUpdateSystem components, PhysicSystem physics,
            GameObjectAddRemoteSystem objects, DatabaseMagicParser magics, BotPersonaService personas,
            BotCounterEvaluator counters, @Qualifier("playgroundClock") Clock clock) {
        super(mmr, users, context, parameters, frames, actions, bots, fever, initial, components, physics,
                objects, magics, personas, counters);
        this.clock = clock;
    }

    @Override public void init(SessionObject session, Runnable onTerminated) {
        expiresAt = clock.instant().plus(LIFETIME);
        getGameContext().setObjectInitializer(object -> {
            if (object.getType() == PrefabType.Player) {
                Mob mob = object.getComponent(Mob.class);
                if (mob != null) mob.overrideMaxHp(PLAYER_HP);
                getGameContext().getGameSessionData().getPlayerData(object.getMaster()).hp = PLAYER_HP;
            } else if (object.getMaster() == Master.LeftPlayer || object.getMaster() == Master.RightPlayer) {
                object.addComponent(new PlaygroundImmunity(object, this));
                object.flushComponents();
            }
        });
        super.init(session, onTerminated);
        session.getPingChecker().close();
        getGameContext().getGameTimer().overrideDuration(LIFETIME.toMillis());
        getGameContext().setResultChecker(new ResultChecker(session) {
            @Override public void setLoser(Master loser) {}
            @Override public boolean checkResult() { return false; }
        });
    }

    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired() { return !clock.instant().isBefore(expiresAt); }
    public boolean acceptsCommands() { return !ended && is_running() && !isExpired(); }
    public boolean isImmune(Master side) {
        return side == Master.LeftPlayer ? leftImmune : side == Master.RightPlayer && rightImmune;
    }

    public PlaygroundReply setImmune(Master side, boolean enabled) {
        if (side == Master.LeftPlayer) leftImmune = enabled;
        else if (side == Master.RightPlayer) rightImmune = enabled;
        else throw new IllegalArgumentException("Select LeftPlayer or RightPlayer.");
        return reply(true, "Damage immunity " + (enabled ? "enabled." : "disabled."));
    }

    public PlaygroundReply clear(Master side) {
        var data = getGameContext().getGameSessionData();
        var candidates = new ArrayList<GameObject>(data.gameObjects);
        candidates.addAll(data.gameObjectsToAdd);
        int removed = 0;
        for (GameObject object : candidates) {
            if (shouldClear(object, side)) {
                object.discard();
                removed++;
            }
        }
        data.gameObjects.removeIf(object -> shouldClear(object, side));
        data.gameObjectsToAdd.removeIf(object -> shouldClear(object, side));
        return reply(true, "Removed " + removed + " objects.");
    }

    public static boolean shouldClear(GameObject object, Master side) {
        return object.getType() != PrefabType.Player && object.getType() != PrefabType.Wall
                && (object.getMaster() == Master.LeftPlayer || object.getMaster() == Master.RightPlayer)
                && (side == Master.None || object.getMaster() == side);
    }

    public CompletableFuture<PlaygroundReply> command(String name, Supplier<PlaygroundReply> action) {
        var result = new CompletableFuture<PlaygroundReply>();
        if (!acceptsCommands()) {
            result.complete(reply(false, "Playground expired or closed."));
        } else if (!getGameContext().submitAction(name, () -> {
            // A timed-out HTTP request must not cast later when a stalled loop resumes.
            if (result.isDone()) return;
            if (!acceptsCommands()) {
                result.complete(reply(false, "Playground expired or closed."));
                return;
            }
            try { result.complete(action.get()); }
            catch (Exception e) { result.complete(reply(false, e.getMessage() == null ? "Command failed." : e.getMessage())); }
        })) {
            result.complete(reply(false, "Command queue is full."));
        }
        return result.orTimeout(3, TimeUnit.SECONDS);
    }

    public PlaygroundReply reply(boolean success, String message) {
        return new PlaygroundReply(success, message, leftImmune, rightImmune);
    }

    public void end(String reason) {
        if (ended) return;
        ended = true;
        try {
            sessionObject.sendFrameInfo(sessionObject.getLeftUserId(), new EndMessage("playgroundEnded", reason));
        } finally {
            close();
        }
    }

    @Override protected void beforeResultCheck() {
        if (isExpired()) end("EXPIRED");
    }
    @Override protected boolean hasTimeLimit() { return false; }
    @Override protected void handleGameEnd() { end("FAILED"); }
    @Override public void activateBotForUser(long userId) {}
    @Override public void deactivateBotForUser(long userId) {}

    private record EndMessage(String type, String reason) {}
}
