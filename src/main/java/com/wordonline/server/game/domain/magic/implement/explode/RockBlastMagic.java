package com.wordonline.server.game.domain.magic.implement.explode;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.RockRemnantSize;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("rock_blast")
public class RockBlastMagic extends Magic {

    private final GameObjectParameters parameters;

    public RockBlastMagic(Parameters parameters) {
        this.parameters = parameters.object(GameObjectKey.ROCK_BLAST);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        if (position == null) {
            return;
        }

        List<GameObject> remnants = gameContext.overlapSphereAll(
                        position,
                        parameters.floatValue(ParameterKey.RADIUS)
                ).stream()
                .filter(GameObject::isActive)
                .filter(object -> RockRemnantSize.fromPrefab(object.getType()).isPresent())
                .toList();

        for (GameObject remnant : remnants) {
            RockRemnantSize size = RockRemnantSize.fromPrefab(remnant.getType()).orElseThrow();
            Vector3 explosionPosition = new Vector3(remnant.getPosition());
            Master remnantMaster = remnant.getMaster();
            remnant.destroy();
            new GameObject(master, PrefabType.RockExplode, explosionPosition, gameContext);
            size.blastRemainderPrefab().ifPresent(prefabType ->
                    new GameObject(remnantMaster, prefabType, new Vector3(explosionPosition), gameContext)
            );
        }
    }
}
