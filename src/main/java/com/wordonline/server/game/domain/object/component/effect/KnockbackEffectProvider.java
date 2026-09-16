package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.Explode;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.dto.Effect;

public class KnockbackEffectProvider extends EffectProvider {

    private Vector3 GetDirection(GameObject otherObject)
    {
        if(gameObject.getComponent(Shot.class) != null)
        {
            return gameObject.getComponent(Shot.class).getDirection();
        }
        else if(gameObject.getComponent(Explode.class) != null)
        {
            return otherObject.getPosition().subtract(this.gameObject.getPosition()).normalize();
        }

        return Vector3.ZERO;
    }

    private float GetProximity(GameObject otherObject)
    {
        if(gameObject.getComponent(Shot.class) != null)
        {
            return (float) (gameObject.getPosition().distance(otherObject.getPosition()) / gameObject.getFirstCircleCollider().map(collider -> (double) collider.getRadius()).orElse(1d));
        }
        else if(gameObject.getComponent(Explode.class) != null)
        {
            return (float) (gameObject.getPosition().distance(otherObject.getPosition()) / gameObject.getComponent(Explode.class).getRadius());
        }

        return 0;
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        EffectReceiver effectReceiver = (EffectReceiver) otherObject.getComponent(EffectReceiver.class);
        if (effectReceiver != null) {
            effectReceiver.onReceive(effect, GetDirection(otherObject),GetProximity(otherObject));
        }
    }

    public KnockbackEffectProvider(GameObject gameObject, Effect effect) {
        super(gameObject, effect);
    }
}
