package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

// The line of vines that vine_toss lays from the caster toward the target. Its count, spacing and
// spawn interval are the vine_toss parameters.
public final class VineLine {

    private VineLine() {
    }

    public static void spawn(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 direction) {
        var vineTossParameters = gameContext.getParameters().object(GameObjectKey.VINE_TOSS);
        int vineCount = vineTossParameters.intValue(ParameterKey.VINE_COUNT);
        float vineSpacing = vineTossParameters.floatValue(ParameterKey.VINE_SPACING);
        float vineSpawnInterval = vineTossParameters.floatValue(ParameterKey.VINE_SPAWN_INTERVAL);

        VineHitTracker hitTracker = new VineHitTracker();
        Vector3 firstPosition = castOrigin.plus(direction.multiply(vineSpacing));
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
                vineSpacing,
                vineSpawnInterval,
                vineCount,
                hitTracker
        ));
    }
}
