package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Status;

import java.util.List;
import com.wordonline.server.game.util.IntervalTimer;

public class RazorGale extends MagicComponent {

    private final float radius;
    private final float attackInterval;
    private final AttackInfo attackInfo;

    private float counter;

    public RazorGale(GameObject gameObject, int damage, float radius, float attackInterval) {
        super(gameObject);
        this.radius = radius;
        this.attackInterval = attackInterval;
        this.attackInfo = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        this.counter = 0f;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, radius, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        float deltaTime = getGameContext().getDeltaTime();
        counter += deltaTime;
        if (counter < attackInterval) {
            return;
        }

        counter = IntervalTimer.carryOver(counter, attackInterval, deltaTime);
        List<GameObject> targets = getGameContext().overlapSphereAll(gameObject, radius);
        targets.stream()
                .filter(target -> target != gameObject)
                .forEach(this::damage);
    }

    private void damage(GameObject target) {
        List<Damageable> damageables = target.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }

        target.setStatus(Status.Damaged);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
    }
}
