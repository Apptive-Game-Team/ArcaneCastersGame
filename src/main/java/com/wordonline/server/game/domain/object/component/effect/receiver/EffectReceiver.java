package com.wordonline.server.game.domain.object.component.effect.receiver;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.EffectApplication;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Effect;

public interface EffectReceiver extends Collidable {
    void onReceive(Effect effect);

    /**
     * Receives an effect together with its source. Receivers that do not care who sent it
     * fall through to {@link #onReceive(Effect)}.
     */
    default void onReceive(EffectApplication application) {
        onReceive(application.effect());
    }

    default void onReceive(Effect effect, float duration) {
        onReceive(effect);
    }

    void onReceive(Effect effect, Vector3 direction, float prox);

    @Override
    default void onCollisionWithEnemy(GameObject otherObject) {}
}
