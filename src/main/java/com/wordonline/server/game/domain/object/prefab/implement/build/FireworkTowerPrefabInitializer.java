package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.FireworkLauncher;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("firework_tower_prefab")
public class FireworkTowerPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public FireworkTowerPrefabInitializer(Parameters parameters) {
        super(PrefabType.FireworkTower);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var fireworkTowerParameters = parameters.object(GameObjectKey.FIREWORK_TOWER);

        // firework_tower.attack_range is not read here: it exists only so the
        // client indicator can draw the blast radius before it happens. The
        // real explosion radius comes from firework_shell.radius, and the
        // database migration is what keeps the two equal. If they ever drift
        // apart the indicator will show a blast area that does not match the
        // real explosion.
        gameObject.getComponents().add(new RigidBody(gameObject, fireworkTowerParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, fireworkTowerParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new DummyMob(gameObject, fireworkTowerParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new FireworkLauncher(
                gameObject,
                fireworkTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                fireworkTowerParameters.floatValue(ParameterKey.ATTACK_OFFSET)
        ));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, fireworkTowerParameters.floatValue(ParameterKey.DURATION)));
        gameObject.getComponents().add(new BuildingEffectReceiver(gameObject));
        gameObject.setElement(ElementType.FIRE);
    }
}
