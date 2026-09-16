package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.OnStartAttacker;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("firework_shell_prefab")
public class FireworkShellPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public FireworkShellPrefabInitializer(Parameters parameters) {
        super(PrefabType.FireworkShell);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var fireworkShellParameters = parameters.object(GameObjectKey.FIREWORK_SHELL);

        // firework_shell.radius doubles as both the collider size and the
        // OnStartAttacker blast range below. It must stay equal to
        // firework_tower.attack_range (checked by the database migration) or
        // the client indicator will advertise a blast area that does not
        // match this explosion.
        float radius = fireworkShellParameters.floatValue(ParameterKey.RADIUS);

        gameObject.addCollider(new CircleCollider(gameObject, radius, true));
        gameObject.setElement(ElementType.FIRE);
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, fireworkShellParameters.floatValue(ParameterKey.DURATION)));
        gameObject.addComponent(new OnStartAttacker(
                gameObject,
                radius,
                fireworkShellParameters.intValue(ParameterKey.DAMAGE)
        ));
    }
}
