package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.PveShieldComponent;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.domain.pve.PveScenario;
import com.wordonline.server.game.domain.pve.PveScenarioAction;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveSetBgmAction;
import com.wordonline.server.game.domain.pve.PveSetSpawnerAction;
import com.wordonline.server.game.domain.pve.PveShield;
import com.wordonline.server.game.domain.pve.PveSpawnWaveAction;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.pve.PveScriptEventDto;
import com.wordonline.server.game.dto.pve.PveStateDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import com.wordonline.server.game.service.pve.PveStateStore;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Scope("prototype")
public class PveScriptSystem implements GameSystem {

    @Setter
    private PveScenario scenario;

    @Setter
    private PveScenarioInstaller.RuntimeState runtime;

    @Setter
    private PveScenarioInstaller installer;

    /** How many seconds back a {@code pveSync} request replays script events. */
    static final int REPLAY_SECONDS = 10;
    private static final int REPLAY_FRAMES = REPLAY_SECONDS * GameLoop.FPS;

    private record SentEvent(PveScriptEventDto dto, int frameNum) {
    }

    private final List<SentEvent> sentEvents = new ArrayList<>();
    private int lastSeq;

    // State channels are not replayed by age: pveSync always carries every channel set so far.
    private final PveStateStore stateStore = new PveStateStore();

    private final Set<String> fired = new HashSet<>();
    private final Set<GameObject> shieldAttached = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void update(GameContext gameContext) {
        if (scenario == null) {
            return;
        }
        if (installer != null) {
            installer.finishPendingSetup();
            attachShields(gameContext);
        }

        for (PveScenarioEvent eventSpec : scenario.events()) {
            if (fired.contains(eventSpec.id())) {
                continue;
            }
            if (!isSatisfiedSafely(eventSpec, gameContext)) {
                continue;
            }
            // Marked fired first, so an event whose trigger or action throws is not retried
            // every frame.
            fired.add(eventSpec.id());
            try {
                sendDialogueIfAny(eventSpec, gameContext);
            } catch (RuntimeException e) {
                log.error("[PVE] dialogue failed; event: {}", eventSpec.id(), e);
            }
            runActions(eventSpec, gameContext);
        }
    }

    // A shielded installer gets its PveShieldComponent the first frame it is installed (it may be
    // installed late by an InstallObject action). Whether the shield is up is not decided here:
    // the component asks again on every damage attempt.
    private void attachShields(GameContext gameContext) {
        List<PveShield> shields = scenario.shields();
        if (shields == null || shields.isEmpty()) {
            return;
        }
        for (PveShield shield : shields) {
            GameObject target = resolveTarget(shield.installerId(), gameContext);
            if (target == null || target.isDestroyed() || !shieldAttached.add(target)) {
                continue;
            }
            List<String> sourceIds = shields.stream()
                    .filter(row -> row.installerId().equals(shield.installerId()))
                    .map(PveShield::sourceInstallerId)
                    .toList();
            target.addComponent(new PveShieldComponent(target, () -> isAnySourceAlive(sourceIds, gameContext)));
        }
    }

    // A source that was never installed does not count.
    private boolean isAnySourceAlive(List<String> sourceIds, GameContext gameContext) {
        for (String sourceId : sourceIds) {
            GameObject source = resolveTarget(sourceId, gameContext);
            if (source != null && !PveObjectiveTarget.isTerminal(source)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSatisfied(PveScenarioEvent eventSpec, GameContext gameContext) {
        return switch (eventSpec.type()) {
            case FrameNumGte -> gameContext.getFrameNum() >= eventSpec.value();
            case SecondsGte -> gameContext.getFrameNum() >= eventSpec.value() * GameLoop.FPS;
            case InstallerHpPercentLte -> isHpPercentLte(eventSpec, gameContext);
            case InstallerDestroyed -> isInstallerDestroyed(eventSpec, gameContext);
        };
    }

    private boolean isHpPercentLte(PveScenarioEvent eventSpec, GameContext gameContext) {
        GameObject target = resolveTarget(eventSpec.targetInstallerId(), gameContext);
        if (target == null) {
            return false;
        }
        Mob mob = PveScenarioInstaller.findHealthMob(target);
        if (mob == null || mob.getMaxHp() <= 0 || mob.getHp() <= 0) {
            return false;
        }
        long percent = Math.round(mob.getHp() * 100.0 / mob.getMaxHp());
        return percent <= eventSpec.value();
    }

    private boolean isInstallerDestroyed(PveScenarioEvent eventSpec, GameContext gameContext) {
        GameObject target = resolveTarget(eventSpec.targetInstallerId(), gameContext);
        // Not installed yet: the event does not fire.
        return target != null && PveObjectiveTarget.isTerminal(target);
    }

    private GameObject resolveTarget(String installerId, GameContext gameContext) {
        return installer == null ? null : installer.getInstalledObject(gameContext, installerId);
    }

    private void sendDialogueIfAny(PveScenarioEvent eventSpec, GameContext gameContext) {
        if (eventSpec.lines() == null || eventSpec.lines().isEmpty()) {
            return;
        }
        int speakerObjectId = runtime == null ? -1 : runtime.getInstalledObjectId(eventSpec.speakerInstallerId());
        int frameNum = gameContext.getFrameNum();
        var event = new PveScriptEventDto(eventSpec.key(), speakerObjectId, eventSpec.lines(), ++lastSeq);
        pruneSentEvents(frameNum);
        sentEvents.add(new SentEvent(event, frameNum));
        long leftId = gameContext.getSessionObject().getLeftUserId();
        long rightId = gameContext.getSessionObject().getRightUserId();
        gameContext.getSessionObject().sendFrameInfo(leftId, event);
        gameContext.getSessionObject().sendFrameInfo(rightId, event);
    }

    private void pruneSentEvents(int frameNum) {
        sentEvents.removeIf(sent -> frameNum - sent.frameNum() > REPLAY_FRAMES);
    }

    /**
     * Sends one user the events sent live within the last {@value #REPLAY_SECONDS} seconds whose
     * seq is greater than {@code lastEventSeq}, oldest first.
     */
    public void sendRecentEventsTo(GameContext gameContext, long userId, int lastEventSeq) {
        int frameNum = gameContext.getFrameNum();
        pruneSentEvents(frameNum);
        for (SentEvent sent : sentEvents) {
            if (sent.dto().seq() > lastEventSeq) {
                gameContext.getSessionObject().sendFrameInfo(userId, sent.dto());
            }
        }
    }

    /**
     * Sends one user one {@code pveState} message for every state channel set so far in this
     * match, oldest seq first, however old the change is and whatever seq the client last saw.
     */
    public void sendStatesTo(GameContext gameContext, long userId) {
        for (PveStateDto state : stateStore.snapshot()) {
            gameContext.getSessionObject().sendFrameInfo(userId, state);
        }
    }

    private void runActions(PveScenarioEvent eventSpec, GameContext gameContext) {
        for (PveScenarioAction action : eventSpec.actions()) {
            // One broken action (a bad prefab, a missing installer) is logged and skipped; the
            // event's other actions and the match go on.
            try {
                if (action instanceof PveSpawnWaveAction spawnWave) {
                    runSpawnWave(spawnWave, gameContext);
                } else if (action instanceof PveInstallObjectAction installObject) {
                    runInstallObject(installObject, gameContext);
                } else if (action instanceof PveSetSpawnerAction setSpawner) {
                    runSetSpawner(setSpawner, gameContext);
                } else if (action instanceof PveSetBgmAction setBgm) {
                    setState(PveStateStore.BGM, setBgm.bgmKey(), gameContext);
                }
            } catch (RuntimeException e) {
                log.error("[PVE] action failed, skipped; event: {}, action: {}", eventSpec.id(), action, e);
            }
        }
    }

    private boolean isSatisfiedSafely(PveScenarioEvent eventSpec, GameContext gameContext) {
        try {
            return isSatisfied(eventSpec, gameContext);
        } catch (RuntimeException e) {
            // A trigger that cannot be evaluated never fires, rather than ending the match.
            fired.add(eventSpec.id());
            log.error("[PVE] trigger failed, event disabled; event: {}", eventSpec.id(), e);
            return false;
        }
    }

    private void setState(String channel, String value, GameContext gameContext) {
        stateStore.set(channel, value).ifPresent(state -> {
            long leftId = gameContext.getSessionObject().getLeftUserId();
            long rightId = gameContext.getSessionObject().getRightUserId();
            gameContext.getSessionObject().sendFrameInfo(leftId, state);
            gameContext.getSessionObject().sendFrameInfo(rightId, state);
        });
    }

    private void runSpawnWave(PveSpawnWaveAction action, GameContext gameContext) {
        int count = Math.max(1, action.count());
        float centerOffset = (count - 1) / 2f;
        for (int i = 0; i < count; i++) {
            float offsetX = (i - centerOffset) * Spawner.DEFAULT_BURST_SPACING;
            Vector3 position = new Vector3(action.positionX() + offsetX, 0, action.positionZ());
            new GameObject(Master.RightPlayer, action.prefabType(), position, gameContext);
        }
    }

    private void runInstallObject(PveInstallObjectAction action, GameContext gameContext) {
        if (installer == null) {
            return;
        }
        PveInstallObject spec = new PveInstallObject(
                action.installerId(),
                action.prefabType(),
                Master.RightPlayer,
                new Vector3(action.positionX(), 0, action.positionZ()),
                action.maxHp()
        );
        installer.installOne(spec, gameContext);
    }

    private void runSetSpawner(PveSetSpawnerAction action, GameContext gameContext) {
        if (installer == null) {
            return;
        }
        GameObject target = installer.getInstalledObject(gameContext, action.installerId());
        if (target == null) {
            return;
        }

        Spawner existingSpawner = target.getComponent(Spawner.class);
        if (existingSpawner != null) {
            target.removeComponent(existingSpawner);
        }

        if (action.count() > 0) {
            float interval = action.intervalSeconds() == null
                    ? Spawner.DEFAULT_SPAWN_INTERVAL_SEC
                    : action.intervalSeconds();
            target.addComponent(new Spawner(target, 0, action.prefabType(), interval, false, action.count()));
        }
    }
}
