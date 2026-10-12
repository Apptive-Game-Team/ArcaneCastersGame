package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;

public class VineToss extends Shot {

    public VineToss(GameObject gameObject) {
        super(gameObject, 0, 0f);
    }

    @Override
    public void setTarget(Vector3 targetPosition) {
        Vector3 direction = targetPosition.subtract(gameObject.getPosition()).normalize();
        if (direction.equals(Vector3.ZERO)) {
            gameObject.destroy();
            return;
        }

        VineLine.spawn(getGameContext(), gameObject.getMaster(), gameObject.getPosition(), direction);

        gameObject.destroy();
    }
}
