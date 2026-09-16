package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.physic.Collidable;

/**
 * Overcharges the caster's own lightning summons on contact.
 * <p>
 * Friendly collisions never reach {@code onCollisionWithEnemy}, so this reaction rides the
 * all-collision hook instead. The shock is delivered through
 * {@link LightningSummonEffectReceiver}, which is what turns it into an overcharge, so only
 * lightning summons react and the overcharge duration stays defined in one place.
 */
public class AllyOverchargeProvider extends Component implements Collidable {

    public AllyOverchargeProvider(GameObject gameObject) {
        super(gameObject);
    }

    @Override
    public void onCollision(GameObject otherObject) {
        if (!EffectApplication.isFriendly(gameObject, otherObject)) {
            return;
        }

        LightningSummonEffectReceiver.overcharge(otherObject);
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
    }

    @Override
    public void onDestroy() {
    }
}
