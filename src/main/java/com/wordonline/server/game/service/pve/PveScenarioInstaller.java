package com.wordonline.server.game.service.pve;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.Mob;
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


        public int getInstalledObjectId(String installerId) {
            return installedObjectIds.getOrDefault(installerId, -1);
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
        runtime.getInstalledObjectIds().put(installObject.installerId(), gameObject.getId());
        return gameObject;
    }

    public GameObject getInstalledObject(GameContext gameContext, String installerId) {
        if (runtime == null || installerId == null || installerId.isBlank()) {
            return null;
        }

        int objectId = runtime.getInstalledObjectId(installerId);
        if (objectId < 0) {
            return null;
        }

        for (GameObject gameObject : gameContext.getGameObjects()) {
            if (gameObject.getId() == objectId) {
                return gameObject;
            }
        }
        return null;
    }

    private void applyMaxHpOverride(GameObject gameObject, Integer maxHp) {
        if (maxHp == null) {
            return;
        }
        Mob mob = gameObject.getComponent(Mob.class);
        if (mob != null) {
            mob.overrideMaxHp(maxHp);
        }
    }
}
