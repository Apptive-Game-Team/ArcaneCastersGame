package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.SequentialLineSpawner;
import com.wordonline.server.game.domain.object.component.magic.VineHitTracker;
import com.wordonline.server.game.domain.object.component.magic.VineSpawnContext;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("vine_toss")
public class VineTossMagic extends Magic {

    private static final int VINE_COUNT = 6;
    private static final float VINE_SPACING = 1f;
    private static final float VINE_SPAWN_INTERVAL = 0.12f;

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        run(gameContext, master, gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null), position);
    }

    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 targetPosition) {
        if (castOrigin == null || targetPosition == null) {
            return;
        }

        Vector3 direction = targetPosition.subtract(castOrigin).normalize();
        if (direction.equals(Vector3.ZERO)) {
            return;
        }

        VineHitTracker hitTracker = new VineHitTracker();
        Vector3 firstPosition = castOrigin.plus(direction.multiply(VINE_SPACING));
        final GameObject[] firstVineRef = new GameObject[1];
        VineSpawnContext.runWithTracker(hitTracker, () ->
                firstVineRef[0] = new GameObject(master, PrefabType.Vine, firstPosition, gameContext)
        );
        GameObject firstVine = firstVineRef[0];
        firstVine.addComponent(new SequentialLineSpawner(
                firstVine,
                master,
                PrefabType.Vine,
                firstPosition,
                direction,
                VINE_SPACING,
                VINE_SPAWN_INTERVAL,
                VINE_COUNT,
                hitTracker
        ));
    }
}
