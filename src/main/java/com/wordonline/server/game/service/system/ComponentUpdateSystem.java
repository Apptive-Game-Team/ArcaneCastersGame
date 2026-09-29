package com.wordonline.server.game.service.system;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ComponentUpdateSystem implements GameSystem {

    // This system is a singleton shared by every session's loop thread, so the objects already
    // reported are keyed by instance (ids repeat across sessions), weakly so a finished match's
    // objects are collected, and behind a lock since loops run on different threads.
    private final Set<GameObject> failedObjects =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<GameObject, Boolean>()));

    @Override
    public void update(GameContext gameContext) {
        for (GameObject gameObject : gameContext.getGameObjects()) {
            if (gameObject.getStatus() == Status.Destroyed) {
                continue;
            }
            try {
                gameObject.update();
            } catch (RuntimeException e) {
                // A bug in one object's components skips that object for this frame instead of
                // ending the match. Logged once per object so a failure that repeats every frame
                // does not flood the log.
                if (failedObjects.add(gameObject)) {
                    log.error("[GameObject] update failed, skipped; id: {}, type: {}",
                            gameObject.getId(), gameObject.getType(), e);
                }
            }
        }
    }
}
