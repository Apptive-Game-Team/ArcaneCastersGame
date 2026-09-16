package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

public class Rune extends Component implements Collidable {

    public Rune(GameObject gameObject) {
        super(gameObject);
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

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        explode();
        gameObject.destroy();
    }

    private void explode() {
        var runeParameters = getGameContext().getParameters().object(GameObjectKey.RUNE);
        float radius = runeParameters.floatValue(ParameterKey.ATTACK_RANGE);
        int damage = runeParameters.intValue(ParameterKey.DAMAGE);
        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        getGameContext().getPhysics()
                .overlapSphereAll(gameObject, radius)
                .forEach(target -> {
                    target.getComponentOptional(Mob.class)
                            .ifPresent(mob -> mob.onDamaged(attackInfo));
                });
    }
}
