package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;

public class WallCollision extends Component implements Collidable {

    public WallCollision(GameObject gameObject) {
        super(gameObject);
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
