package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.object.GameObject;

public interface Collidable {

    /**
     * Called when the two objects belong to different masters, excluding map walls,
     * so an object never damages or debuffs its own side or attacks a boundary.
     */
    void onCollisionWithEnemy(GameObject otherObject);

    /**
     * Called for every collision, friendly ones included. Reactions that must also
     * fire on allied objects, such as overcharging one's own lightning summons,
     * belong here.
     */
    default void onCollision(GameObject otherObject) {
    }
}
