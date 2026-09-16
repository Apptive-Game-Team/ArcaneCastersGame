package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.LightningCloud;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("lightning_cloud_prefab")
public class LightningCloudPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public LightningCloudPrefabInitializer(Parameters parameters) {
        super(PrefabType.LightningCloud);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var cloudParameters = parameters.object(GameObjectKey.LIGHTNING_CLOUD);
        // the strike itself is still tuned under the lightning_drop object, from when each
        // strike was a spawned drop
        var strikeParameters = parameters.object(GameObjectKey.LIGHTNING_DROP);
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new LightningCloud(
                gameObject,
                cloudParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                cloudParameters.intValue(ParameterKey.QUANTITY),
                strikeParameters.intValue(ParameterKey.DAMAGE),
                strikeParameters.floatValue(ParameterKey.RADIUS)));
    }
}
