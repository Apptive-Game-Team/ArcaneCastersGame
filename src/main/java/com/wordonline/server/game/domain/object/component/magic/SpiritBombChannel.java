package com.wordonline.server.game.domain.object.component.magic;

import java.util.Comparator;
import java.util.EnumSet;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;

public class SpiritBombChannel extends Component {

    public static final float CHARGE_DURATION = 0.4f;
    public static final float BEAM_DURATION = 4f;
    public static final float TICK_INTERVAL = 1f;
    public static final float BEAM_WIDTH = 0.75f;
    public static final String BEAM_PROJECTILE = "SpiritBombBeam";

    private static final int TICK_COUNT = 4;
    private static final float VISUAL_TICK_DURATION = 1.05f;

    /**
     * 빔이 보이는 굵기를 피해량으로 정하는 구간. 이 마법의 피해량은 흡수한 아군 체력의
     * {@link #DAMAGE_FRACTION} 배라 시전마다 다르고, 몇 마리를 얼마나 빨았느냐에 따라 백 단위에서
     * 천 단위까지 벌어진다. 그래서 굵기를 피해량에 그대로 비례시키지 않고 이 구간 안에서만 늘린다 —
     * 작은 한 방도 눈에 보이고, 큰 한 방도 화면을 덮지 않는다.
     *
     * <p>{@link #BEAM_WIDTH} 와 다른 값이라는 점에 주의하라. 그쪽은 대상을 고르는 <b>판정</b> 폭이고
     * 피해량과 무관하게 고정이다. 여기 값은 보이는 굵기일 뿐이라 둘은 일부러 갈라져 있다.
     */
    private static final int MIN_WIDTH_DAMAGE = 100;
    private static final int MAX_WIDTH_DAMAGE = 1000;
    private static final float MIN_VISUAL_WIDTH = 0.25f;
    private static final float MAX_VISUAL_WIDTH = 1f;

    private final Vector3 direction;
    private final float beamLength;
    private final int totalDamage;
    private float elapsed;
    private int ticksApplied;

    public SpiritBombChannel(GameObject player, Vector3 targetPosition, int totalDamage) {
        super(player);
        Vector3 origin = player.getPosition().grounded();
        Vector3 offset = targetPosition.grounded().subtract(origin);
        this.direction = offset.normalize();
        this.beamLength = (float) offset.distance(Vector3.ZERO);
        this.totalDamage = totalDamage;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        elapsed += getGameContext().getDeltaTime();
        if (elapsed < CHARGE_DURATION || direction.equals(Vector3.ZERO)) {
            return;
        }

        int dueTicks = Math.min(
                TICK_COUNT,
                (int) Math.floor((elapsed - CHARGE_DURATION) / TICK_INTERVAL) + 1
        );
        while (ticksApplied < dueTicks) {
            applyTick(ticksApplied);
            ticksApplied++;
        }

        if (ticksApplied == TICK_COUNT) {
            gameObject.removeComponent(this);
        }
    }

    private void applyTick(int tickIndex) {
        GameObject target = findClosestTarget();
        if (target == null) {
            return;
        }

        int baseDamage = totalDamage / TICK_COUNT;
        int remainder = totalDamage % TICK_COUNT;
        int tickDamage = baseDamage + (tickIndex < remainder ? 1 : 0);

        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, target, BEAM_PROJECTILE, VISUAL_TICK_DURATION, visualWidth());

        if (tickDamage == 0) {
            return;
        }

        AttackInfo attackInfo = new AttackInfo(
                tickDamage,
                EnumSet.of(ElementType.LIGHTNING, ElementType.NATURE)
        ).withAttacker(gameObject);
        target.getComponents(Damageable.class)
                .forEach(damageable -> damageable.onDamaged(attackInfo));
    }

    /**
     * 이 한 방의 피해량을 보이는 굵기로 바꾼다. 구간 밖은 양 끝 값으로 자른다.
     */
    static float visualWidth(int totalDamage) {
        if (totalDamage <= MIN_WIDTH_DAMAGE) {
            return MIN_VISUAL_WIDTH;
        }

        if (totalDamage >= MAX_WIDTH_DAMAGE) {
            return MAX_VISUAL_WIDTH;
        }

        float progress = (float) (totalDamage - MIN_WIDTH_DAMAGE) / (MAX_WIDTH_DAMAGE - MIN_WIDTH_DAMAGE);
        return MIN_VISUAL_WIDTH + progress * (MAX_VISUAL_WIDTH - MIN_VISUAL_WIDTH);
    }

    private float visualWidth() {
        return visualWidth(totalDamage);
    }

    private GameObject findClosestTarget() {
        Vector3 origin = gameObject.getPosition().grounded();
        Vector3 end = origin.plus(direction.multiply(beamLength));

        return getGameContext().getActiveGameObjects().stream()
                .filter(target -> TargetRelation.canAttack(gameObject, target))
                .filter(target -> target.hasComponent(Damageable.class))
                .filter(target -> {
                    float targetRadius = target.getFirstCircleCollider()
                            .map(collider -> collider.getRadius())
                            .orElse(0f);
                    return distanceToSegment(target.getPosition().grounded(), origin, end)
                            <= BEAM_WIDTH + targetRadius;
                })
                .min(Comparator.comparingDouble(target -> target.getPosition().distance(origin)))
                .orElse(null);
    }

    static double distanceToSegment(Vector3 point, Vector3 start, Vector3 end) {
        Vector3 segment = end.subtract(start);
        float lengthSquared = segment.dot(segment);
        if (lengthSquared == 0f) {
            return point.distance(start);
        }

        float progress = Math.clamp(point.subtract(start).dot(segment) / lengthSquared, 0f, 1f);
        Vector3 closest = start.plus(segment.multiply(progress));
        return point.distance(closest);
    }

    @Override
    public void onDestroy() {
    }
}
