package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/** Independent, non-homing shell: the damage query runs at the copied landing point on arrival. */
public class CraterShell extends MagicComponent {
    private static final float ARC_HEIGHT = 2.5f;
    private final float speed;
    private final float blastRadius;
    private Vector3 origin;
    private Vector3 impact;
    private AttackInfo attack;
    private float duration;
    private float elapsed;
    private boolean spent;

    public CraterShell(GameObject object, float speed, float blastRadius) {
        super(object);
        this.speed = speed;
        this.blastRadius = blastRadius;
    }

    public void launch(Vector3 target, int damage, GameObject shooter) {
        origin = new Vector3(gameObject.getPosition());
        impact = new Vector3(target.getX(), 0f, target.getZ());
        duration = Math.max(0.1f, (float) origin.grounded().distance(impact) / Math.max(0.1f, speed));
        attack = new AttackInfo(damage, shooter.getElement().total()).withAttacker(shooter);
    }

    @Override
    public void start() {}

    @Override
    public void update() {
        if (spent || impact == null) {
            return;
        }
        elapsed += getGameContext().getDeltaTime();
        float progress = Math.clamp(elapsed / duration, 0f, 1f);
        Vector3 position = Vector3.lerp(origin, impact, progress);
        gameObject.setPosition(new Vector3(position.getX(),
                position.getY() + 4f * ARC_HEIGHT * progress * (1f - progress), position.getZ()));
        if (progress < 1f) {
            return;
        }
        spent = true;
        for (GameObject enemy : getGameContext().overlapSphereAll(gameObject, blastRadius)) {
            if (!enemy.isActive() || enemy.isDying() || !TargetRelation.canAttack(gameObject, enemy)
                    || (TargetMask.of(enemy) & TargetMask.GROUND.bit) == 0) {
                continue;
            }
            for (Damageable damageable : enemy.getComponents(Damageable.class)) {
                damageable.onDamaged(attack);
            }
        }
        new GameObject(gameObject.getMaster(), PrefabType.CraterExplosion,
                new Vector3(impact), getGameContext());
        gameObject.destroy();
    }

    @Override
    public void onDestroy() {}
}
