package com.wordonline.server.game.service.pve;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.service.GameContext;
import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope("prototype")
public class PveScenarioInstaller {

    @Getter
    public static class RuntimeState {
        private final Map<String, Integer> installedObjectIds = new HashMap<>();
        // The installed objects themselves. A new GameObject joins gameContext.getGameObjects()
        // only on the next frame, so looking it up there by id finds nothing right after install;
        // holding the reference keeps "not in the world yet" apart from "destroyed".
        private final Map<String, GameObject> installedObjects = new HashMap<>();

        public int getInstalledObjectId(String installerId) {
            return installedObjectIds.getOrDefault(installerId, -1);
        }

        public GameObject getInstalledGameObject(String installerId) {
            return installerId == null ? null : installedObjects.get(installerId);
        }

        public void register(String installerId, GameObject gameObject) {
            installedObjectIds.put(installerId, gameObject.getId());
            installedObjects.put(installerId, gameObject);
        }
    }

    @Getter
    private RuntimeState runtime;

    public void install(List<PveInstallObject> installers, GameContext gameContext) {
        this.runtime = new RuntimeState();

        for (PveInstallObject installObject : installers) {
            installOne(installObject, gameContext);
        }
    }

    // Installs one object outside the initial batch, for the InstallObject action: registers it
    // under installerId so later events, speakers and objectives can reference it, same as the
    // scenario's own installer rows.
    public GameObject installOne(PveInstallObject installObject, GameContext gameContext) {
        if (runtime == null) {
            runtime = new RuntimeState();
        }

        GameObject gameObject = new GameObject(
                installObject.master(),
                installObject.prefabType(),
                installObject.position(),
                gameContext
        );

        applyMaxHpOverride(gameObject, installObject.maxHp());
        runtime.register(installObject.installerId(), gameObject);
        return gameObject;
    }

    public GameObject getInstalledObject(GameContext gameContext, String installerId) {
        if (runtime == null || installerId == null || installerId.isBlank()) {
            return null;
        }
        return runtime.getInstalledGameObject(installerId);
    }

    private void applyMaxHpOverride(GameObject gameObject, Integer maxHp) {
        if (maxHp == null) {
            return;
        }
        Mob mob = findHealthMob(gameObject);
        if (mob != null) {
            mob.overrideMaxHp(maxHp);
        }
    }

    // The mob whose hp is the structure's hp. A PVE boss carries a Spawner, which is also a Mob
    // (hp 0) and is added before the boss mob, so the first Mob component is the wrong one.
    public static Mob findHealthMob(GameObject gameObject) {
        if (gameObject == null) {
            return null;
        }
        Mob fallback = null;
        for (Mob mob : gameObject.getComponents(Mob.class)) {
            if (mob instanceof PveObjectiveTarget) {
                return mob;
            }
            if (fallback == null && !(mob instanceof Spawner)) {
                fallback = mob;
            }
        }
        return fallback;
    }
}
