package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.directive.RallyMoveDirective;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;

public class RallyingTotem extends MagicComponent {
    private final float fallSpeed;
    private final float duration;
    private final float zoneRadius;
    private final float detectionRange;
    private final float buffDuration;
    private float elapsedTime;
    private boolean landed;

    public RallyingTotem(
            GameObject gameObject,
            float fallSpeed,
            float duration,
            float zoneRadius,
            float detectionRange,
            float buffDuration) {
        super(gameObject);
        this.fallSpeed = fallSpeed;
        this.duration = duration;
        this.zoneRadius = zoneRadius;
        this.detectionRange = detectionRange;
        this.buffDuration = buffDuration;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (!landed) {
            fall();
            return;
        }

        elapsedTime += getGameContext().getDeltaTime();
        if (elapsedTime >= duration) {
            gameObject.destroy();
            return;
        }

        getGameContext().getGameSessionData().gameObjects.stream()
                .filter(this::canRally)
                .forEach(this::applyRally);

        getGameContext().overlapSphereAll(gameObject, zoneRadius).stream()
                .filter(this::canInspire)
                .forEach(this::applyInspired);
    }

    @Override
    public void onDestroy() {
    }

    private void fall() {
        gameObject.setPosition(gameObject.getPosition().plus(0, -fallSpeed * getGameContext().getDeltaTime(), 0));
        if (gameObject.getPosition().getY() < 0) {
            gameObject.setPosition(gameObject.getPosition().grounded());
            landed = true;
        }
    }

    private boolean canRally(GameObject target) {
        if (target == gameObject) return false;
        if (gameObject.getMaster() == Master.None) return false;
        if (target.getMaster() != gameObject.getMaster()) return false;
        if (!target.hasComponent(BehaviorMob.class)) return false;
        return gameObject.getPosition().grounded().distance(target.getPosition().grounded()) <= detectionRange;
    }

    private void applyRally(GameObject target) {
        if (target.getComponent(RallyMoveDirective.class) != null) {
            return;
        }

        boolean pending = target.getComponentsToAdd().stream()
                .anyMatch(RallyMoveDirective.class::isInstance);
        if (!pending) {
            target.addComponent(new RallyMoveDirective(target, gameObject, zoneRadius));
        }
    }

    private boolean canInspire(GameObject target) {
        if (target == gameObject) return false;
        if (gameObject.getMaster() == Master.None) return false;
        if (target.getMaster() != gameObject.getMaster()) return false;
        return target.getComponent(EffectReceiver.class) != null;
    }

    private void applyInspired(GameObject target) {
        target.getComponent(EffectReceiver.class).onReceive(Effect.Inspired, buffDuration);
    }
}
