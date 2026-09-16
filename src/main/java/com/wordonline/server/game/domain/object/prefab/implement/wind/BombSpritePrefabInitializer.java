package com.wordonline.server.game.domain.object.prefab.implement.wind;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BombSpriteMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("bomb_sprite_prefab")
public class BombSpritePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public BombSpritePrefabInitializer(Parameters parameters) {
        super(PrefabType.BombSprite);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var spriteParameters = parameters.object(GameObjectKey.BOMB_SPRITE);

        gameObject.addComponent(new RigidBody(
                gameObject,
                spriteParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(
                gameObject,
                GameConfig.AERIAL_MOB_INIT_HEIGHT));
        gameObject.addCollider(new CircleCollider(
                gameObject,
                spriteParameters.floatValue(ParameterKey.RADIUS),
                false));
        gameObject.addComponent(new BombSpriteMob(
                gameObject,
                spriteParameters.intValue(ParameterKey.HP),
                spriteParameters.floatValue(ParameterKey.SPEED),
                spriteParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                spriteParameters.floatValue(ParameterKey.ATTACK_RANGE)));
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        gameObject.setElement(ElementType.WIND);
    }
}
