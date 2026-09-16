package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class Totem extends Mob {

    private float timer = 0;
    private final AttackInfo attackInfo;
    private final int targetMask;
    private final float healCooldown;
    private final float healRange;

    public Totem(GameObject gameObject, int maxHp, int damage, float attackInterval, float range, int targetMask) {
        super(gameObject, maxHp, 0);
        attackInfo = new AttackInfo(damage, ElementType.NATURE).withAttacker(gameObject);
        this.targetMask = targetMask;
        healCooldown = attackInterval;
        healRange = range;
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, healRange), GizmoCategory.AttackRange);
    }

    @Override
    public void update() {
        super.update();
        timer += getGameContext().getDeltaTime();
        if (timer >= healCooldown) {
            timer = 0;
            //heal circle prefab
            for (GameObject object : getGameContext().getGameSessionData().gameObjects) {
                if (!object.isActive() || !CombatRange.contains(gameObject, object, healRange)) continue;
                if (gameObject.getMaster() != object.getMaster()) continue;
                Mob mob = object.getComponent(Mob.class);
                if(mob != null){
                    mob.applyDamage(attackInfo);
                }
            }
            gameObject.setStatus(Status.Attack);
        }
    }

    @Override
    public void onDestroy() {

    }
}
