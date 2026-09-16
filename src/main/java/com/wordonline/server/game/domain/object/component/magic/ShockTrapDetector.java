package com.wordonline.server.game.domain.object.component.magic;

import java.util.List;

import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.EffectApplyPolicy;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.StunStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.frame.GameEventDto;

/**
 * Arms on placement and stays on the field indefinitely: nothing ages it out. It waits
 * for an opponent to enter {@code radius}, counts down {@code triggerDelay}, then stuns
 * whoever is still inside the radius at that moment and destroys itself, the same
 * one-shot shape as {@link Explode}. The only other way it leaves the field is an enemy
 * killing its {@code DummyMob} hit points.
 */
public class ShockTrapDetector extends Component {

    private final float radius;
    private final float triggerDelay;
    private final float stunDuration;

    private boolean counting = false;
    private boolean triggered = false;
    private float triggerCounter = 0f;

    public ShockTrapDetector(
            GameObject gameObject,
            float radius,
            float triggerDelay,
            float stunDuration) {
        super(gameObject);
        this.radius = radius;
        this.triggerDelay = triggerDelay;
        this.stunDuration = stunDuration;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, radius, GizmoCategory.DetectionRange);
    }

    @Override
    public void update() {
        if (triggered) {
            return;
        }

        if (!counting) {
            if (!detectEnemies().isEmpty()) {
                counting = true;
                triggerCounter = 0f;
                gameObject.addEffect(Effect.ShockTrapArming);
            }
            return;
        }

        triggerCounter += getGameContext().getDeltaTime();
        if (triggerCounter < triggerDelay) {
            return;
        }

        trigger();
    }

    @Override
    public void onDestroy() {
        gameObject.removeEffect(Effect.ShockTrapArming);
    }

    private void trigger() {
        detectEnemies().forEach(this::stun);

        gameObject.removeEffect(Effect.ShockTrapArming);
        gameObject.setStatus(Status.Attack);
        getGameContext().addEvent(GameEventDto.shock(gameObject.getId()));
        triggered = true;
        gameObject.destroy();
    }

    private List<GameObject> detectEnemies() {
        return getGameContext().overlapSphereAll(gameObject, radius).stream()
                .filter(target -> TargetRelation.canAttack(gameObject, target))
                .toList();
    }

    private void stun(GameObject target) {
        CommonEffectReceiver receiver = target.getComponent(CommonEffectReceiver.class);
        if (receiver != null) {
            receiver.applyEffect(
                    StatusEffectKey.TrapStun_Receive,
                    () -> new StunStatusEffect(target, stunDuration, StatusEffectKey.TrapStun_Receive, Effect.Shock),
                    EffectApplyPolicy.REFRESH_DURATION,
                    stunDuration);
            return;
        }

        for (Component component : target.getComponentsToAdd()) {
            if (component instanceof StunStatusEffect effect
                    && effect.getKey() == StatusEffectKey.TrapStun_Receive) {
                effect.refresh(stunDuration);
                return;
            }
        }
        target.addComponent(new StunStatusEffect(target, stunDuration, StatusEffectKey.TrapStun_Receive, Effect.Shock));
    }
}
