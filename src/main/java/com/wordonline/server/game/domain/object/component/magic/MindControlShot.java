package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.simple.Cannon;
import com.wordonline.server.game.domain.object.component.mob.simple.ManaWellMob;
import com.wordonline.server.game.domain.object.component.mob.simple.PlayerHealthComponent;
import com.wordonline.server.game.domain.object.component.mob.simple.Totem;
import com.wordonline.server.game.domain.object.component.mob.simple.Turret;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

public class MindControlShot extends MagicComponent implements Collidable {
    private final float speed;
    private Vector3 direction;

    public MindControlShot(GameObject gameObject, float speed) {
        super(gameObject);
        this.speed = speed;
    }

    public void setTarget(Vector3 targetPosition) {
        direction = targetPosition.subtract(gameObject.getPosition()).normalize();
    }

    @Override
    public void update() {
        if (direction == null) return;
        gameObject.setPosition(gameObject.getPosition().plus(direction.multiply(speed * getGameContext().getDeltaTime())));
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (!isControllableEnemySummon(otherObject)) {
            return;
        }

        otherObject.setMaster(opposite(otherObject.getMaster()));
        otherObject.setStatus(Status.Idle);
        gameObject.destroy();
    }

    private boolean isControllableEnemySummon(GameObject otherObject) {
        if (otherObject == gameObject) return false;
        if (otherObject.getMaster() == gameObject.getMaster()) return false;
        if (otherObject.getMaster() == Master.None) return false;
        if (!otherObject.hasComponent(Mob.class)) return false;

        return !otherObject.hasComponent(PlayerHealthComponent.class)
                && !otherObject.hasComponent(Cannon.class)
                && !otherObject.hasComponent(ManaWellMob.class)
                && !otherObject.hasComponent(Totem.class)
                && !otherObject.hasComponent(Turret.class);
    }

    private Master opposite(Master master) {
        return master == Master.LeftPlayer ? Master.RightPlayer : Master.LeftPlayer;
    }
}
