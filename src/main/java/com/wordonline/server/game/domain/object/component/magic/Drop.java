package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Status;

import java.util.List;

public class Drop extends Mob implements Collidable {
    public static final int SPEED = 5;

    private int direction = 0;
    private final int damage;

    @Override
    public void start() {

    }

    @Override
    public void update() {
        super.update();

        gameObject.setPosition(gameObject.getPosition().plus(0, direction * SPEED * gameObject.getGameContext().getDeltaTime(), 0));
        if (gameObject.getPosition().getY() < 0) {
            gameObject.destroy();
        }
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onDeath() {
        direction = 0;
        gameObject.destroy();
    }

    public Drop(GameObject gameObject, int damage) {
        super(gameObject, 1, 0);

        direction = -1;
        this.damage = damage;
    }

    protected int getDamage() {
        return damage;
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        List<Damageable> damageables = otherObject.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }
        direction = 0;
        gameObject.setStatus(Status.Attack);
        otherObject.setStatus(Status.Damaged);
        damageables.forEach(damageable -> damageable.onDamaged(new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject)));

        gameObject.destroy();
    }
}
