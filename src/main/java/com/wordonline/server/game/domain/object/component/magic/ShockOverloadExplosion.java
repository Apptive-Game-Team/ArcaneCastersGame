package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Status;

import java.util.ArrayList;
import java.util.List;

public class ShockOverloadExplosion extends Explode {

    static final float SECONDARY_DELAY = 1f;
    static final float SECONDARY_RADIUS_MULTIPLIER = 0.5f;
    static final float SECONDARY_DAMAGE_MULTIPLIER = 0.5f;
    private static final float SECONDARY_EFFECT_DURATION = 0.8f;
    private static final String SECONDARY_EFFECT_TYPE = "ShockOverloadSecondary";

    private final List<GameObject> markedTargets = new ArrayList<>();
    private boolean primaryExploded;

    public ShockOverloadExplosion(GameObject gameObject, int damage, float radius) {
        super(gameObject, damage, radius);
    }

    @Override
    public void update() {
        if (!isRunning) {
            return;
        }

        counter += getGameContext().getDeltaTime();

        if (!primaryExploded) {
            if (counter < delay) {
                return;
            }

            explodePrimary();
            primaryExploded = true;
            counter = 0f;
            return;
        }

        if (counter < SECONDARY_DELAY) {
            return;
        }

        explodeMarkedTargets();
        isRunning = false;
        gameObject.destroy();
    }

    private void explodePrimary() {
        for (GameObject target : getGameContext().overlapSphereAll(gameObject, radius)) {
            if (target == gameObject
                    || !TargetRelation.canAttack(gameObject, target)
                    || target.getComponents(Damageable.class).isEmpty()) {
                continue;
            }

            super.handleGameObject(target);
            markedTargets.add(target);
            target.addEffect(Effect.Shock);
        }
    }

    private void explodeMarkedTargets() {
        int secondaryDamage = Math.max(1, Math.round(damage * SECONDARY_DAMAGE_MULTIPLIER));
        float secondaryRadius = radius * SECONDARY_RADIUS_MULTIPLIER;
        AttackInfo secondaryAttack = new AttackInfo(
                secondaryDamage,
                gameObject.getElement().total()
        ).withAttacker(gameObject);

        for (GameObject markedTarget : markedTargets) {
            markedTarget.removeEffect(Effect.Shock);
            if (markedTarget.isDestroyed()) {
                continue;
            }

            Vector3 center = new Vector3(markedTarget.getPosition());
            getGameContext().getObjectsInfoDtoBuilder().createProjection(
                    center,
                    center,
                    SECONDARY_EFFECT_TYPE,
                    SECONDARY_EFFECT_DURATION
            );

            for (GameObject affected : getGameContext().getPhysics().overlapSphereAll(center, secondaryRadius)) {
                if (!TargetRelation.canAttack(gameObject, affected)) {
                    continue;
                }

                List<Damageable> damageables = affected.getComponents(Damageable.class);
                if (damageables.isEmpty()) {
                    continue;
                }

                affected.setStatus(Status.Damaged);
                damageables.forEach(damageable -> damageable.onDamaged(secondaryAttack));
            }
        }
    }

}
