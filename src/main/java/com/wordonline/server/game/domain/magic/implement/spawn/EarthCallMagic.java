package com.wordonline.server.game.domain.magic.implement.spawn;

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

@Component("earth_call")
public class EarthCallMagic extends Magic {

    private final GameObjectParameters parameters;

    public EarthCallMagic(Parameters parameters) {
        this.parameters = parameters.object(GameObjectKey.EARTH_CALL);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        if (position == null) {
            return;
        }

        new GameObject(master, PrefabType.EarthCall, new Vector3(position), gameContext);

        List<GameObject> remnants = gameContext.overlapSphereAll(
                        position,
                        parameters.floatValue(ParameterKey.RADIUS)
                ).stream()
                .filter(GameObject::isActive)
                .filter(object -> RockRemnantSize.fromPrefab(object.getType()).isPresent())
                .toList();

        for (GameObject remnant : remnants) {
            RockRemnantSize size = RockRemnantSize.fromPrefab(remnant.getType()).orElseThrow();
            Vector3 summonPosition = new Vector3(remnant.getPosition());
            remnant.destroy();
            new GameObject(master, size.summonedPrefab(), summonPosition, gameContext);
        }
    }
}
