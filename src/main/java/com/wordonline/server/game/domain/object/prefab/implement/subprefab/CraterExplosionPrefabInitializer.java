package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("crater_explosion_prefab")
public class CraterExplosionPrefabInitializer extends PrefabInitializer {
    private final Parameters parameters;

    public CraterExplosionPrefabInitializer(Parameters parameters) {
        super(PrefabType.CraterExplosion);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject object) {
        // Damage belongs to CraterShell exactly once; this body is presentation only.
        object.drawCircle(Vector3.ZERO, parameters.object(GameObjectKey.CRATER_SHELL)
                .floatValue(ParameterKey.RADIUS), GizmoCategory.AreaOfEffect);
        object.addComponent(new TimedSelfDestroyer(object, 0.6f));
    }
}
