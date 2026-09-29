package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.domain.pve.PveScenario;
import com.wordonline.server.game.domain.pve.PveScenarioAction;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveSetSpawnerAction;
import com.wordonline.server.game.domain.pve.PveSpawnWaveAction;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.pve.PveScriptEventDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@Scope("prototype")
public class PveScriptSystem implements GameSystem {

    @Setter
    private PveScenario scenario;

    @Setter
    private PveScenarioInstaller.RuntimeState runtime;

    @Setter
    private PveScenarioInstaller installer;

    private final Set<String> fired = new HashSet<>();

    @Override
    public void update(GameContext gameContext) {
        if (scenario == null) {
            return;
        }
        if (installer != null) {
            installer.applyPendingMaxHp();
        }

        for (PveScenarioEvent eventSpec : scenario.events()) {
            if (fired.contains(eventSpec.id())) {
                continue;
            }
            if (!isSatisfied(eventSpec, gameContext)) {
                continue;
            }
            fired.add(eventSpec.id());
            sendDialogueIfAny(eventSpec, gameContext);
            runActions(eventSpec, gameContext);
        }
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
        var event = new PveScriptEventDto(eventSpec.key(), speakerObjectId, eventSpec.lines());
        long leftId = gameContext.getSessionObject().getLeftUserId();
        long rightId = gameContext.getSessionObject().getRightUserId();
        gameContext.getSessionObject().sendFrameInfo(leftId, event);
        gameContext.getSessionObject().sendFrameInfo(rightId, event);
    }

    private void runActions(PveScenarioEvent eventSpec, GameContext gameContext) {
        for (PveScenarioAction action : eventSpec.actions()) {
            if (action instanceof PveSpawnWaveAction spawnWave) {
                runSpawnWave(spawnWave, gameContext);
            } else if (action instanceof PveInstallObjectAction installObject) {
                runInstallObject(installObject, gameContext);
            } else if (action instanceof PveSetSpawnerAction setSpawner) {
                runSetSpawner(setSpawner, gameContext);
            }
        }
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
