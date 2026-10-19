package com.wordonline.server.game.domain.object.component.magic;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ReferenceProjectileTarget;
import com.wordonline.server.game.util.RaycastHit;

public class SpiritBombChannel extends Component {

    public static final float CHARGE_DURATION = 0.4f;
    public static final float BEAM_DURATION = 4f;
    public static final float TICK_INTERVAL = 1f;
    public static final String BEAM_PROJECTILE = "SpiritBombBeam";

    private static final int TICK_COUNT = 4;
    private static final float VISUAL_TICK_DURATION = 1.05f;

    /**
     * 빔이 보이는 굵기를 피해량으로 정하는 구간. 이 마법의 피해량은 흡수한 아군 체력의
     * {@link #DAMAGE_FRACTION} 배라 시전마다 다르고, 몇 마리를 얼마나 빨았느냐에 따라 백 단위에서
     * 천 단위까지 벌어진다. 그래서 굵기를 피해량에 그대로 비례시키지 않고 이 구간 안에서만 늘린다 —
     * 작은 한 방도 눈에 보이고, 큰 한 방도 화면을 덮지 않는다.
     *
     * <p>보이는 굵기일 뿐이고 판정과는 무관하다. 판정은 두께 없는 ray 로 한다.
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

    /**
     * 클릭한 위치는 방향만 정한다. 빔은 클릭 거리와 상관없이 {@code beamLength}(마법 사거리)만큼 뻗는다.
     * 높이도 방향에 들어간다. 비행 유닛을 찍으면 client 가 그 유닛의 높이를 실어 보내므로 빔이 위로 간다.
     */
    public SpiritBombChannel(GameObject player, Vector3 targetPosition, int totalDamage, float beamLength) {
        super(player);
        Vector3 origin = player.getPosition();
        Vector3 offset = targetPosition.subtract(origin);
        this.direction = offset.normalize();
        this.beamLength = beamLength;
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
        Set<GameObject> hitTargets = new HashSet<>();
        RaycastHit hit = findClosestHit(hitTargets);
        if (hit == null) {
            sendBeam(beamLength);
            return;
        }

        int baseDamage = totalDamage / TICK_COUNT;
        int remainder = totalDamage % TICK_COUNT;
        int remaining = baseDamage + (tickIndex < remainder ? 1 : 0);

        if (remaining == 0) {
            sendBeam(hit.distance());
            return;
        }

        // 대상이 죽으면 남은 피해를 같은 tick 안에서 ray 위 다음으로 가까운 적에게 넘긴다.
        while (hit != null && remaining > 0) {
            GameObject target = hit.gameObject();
            hitTargets.add(target);
            sendBeam(hit.distance());

            Mob mob = target.getComponent(Mob.class);
            int hpBefore = mob == null ? 0 : mob.getHp();

            AttackInfo attackInfo = new AttackInfo(
                    remaining,
                    EnumSet.of(ElementType.LIGHTNING, ElementType.NATURE)
            ).withAttacker(gameObject);
            target.getComponents(Damageable.class)
                    .forEach(damageable -> damageable.onDamaged(attackInfo));

            // Mob 이 없으면 잃은 체력을 알 수 없다. 면역이나 interceptor 로 체력이 안 깎여도 마찬가지라
            // 여기서 멈춘다. 그래야 같은 tick 안에서 루프가 돌지 않는다.
            if (mob == null) {
                return;
            }
            int hpAfter = mob.getHp();
            int hpLost = hpBefore - hpAfter;
            boolean killed = hpAfter <= 0 || target.isDying() || target.isDestroyed();
            if (hpLost <= 0 || !killed) {
                return;
            }

            // 속성 배율을 거친 뒤라 잃은 체력과 피해량이 다르다. 넘치는 체력의 비율만큼 피해를 남긴다.
            int overkillHp = Math.max(0, -hpAfter);
            remaining = Math.round((float) remaining * overkillHp / hpLost);
            hit = findClosestHit(hitTargets);
        }
    }

    /**
     * 빔은 조준선을 벗어나지 않는다. 맞은 적의 중심으로 꺾지 않고, ray 가 그 적의 몸에 닿은 거리에서 끝낸다.
     */
    private void sendBeam(float length) {
        Vector3 end = gameObject.getPosition().plus(direction.multiply(length));
        getGameContext().getObjectsInfoDtoBuilder().createProjection(
                new ReferenceProjectileTarget(gameObject.getId()),
                new PositionProjectileTarget(end),
                BEAM_PROJECTILE,
                VISUAL_TICK_DURATION,
                visualWidth()
        );
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

    private RaycastHit findClosestHit(Set<GameObject> excluded) {
        return getGameContext()
                .raycastAll(gameObject.getPosition(), direction, beamLength)
                .stream()
                .filter(hit -> !excluded.contains(hit.gameObject()))
                .filter(hit -> TargetRelation.canAttack(gameObject, hit.gameObject()))
                .filter(hit -> hit.gameObject().hasComponent(Damageable.class))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void onDestroy() {
    }
}
