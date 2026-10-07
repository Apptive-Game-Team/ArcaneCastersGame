package com.wordonline.server.game.service;

import java.util.Optional;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * 유닛과 건물을 놓을 자리가 이미 땅 위의 다른 몸과 겹치거나 물 위인지 본다. 공중에 나타나는 소환은 무엇과도
 * 겹쳐 놓을 수 있고, 공중에 떠 있는 몸도 장애물로 치지 않는다. 쏘기·떨구기·폭발처럼 몸을 남기지
 * 않는 마법은 검사하지 않는다.
 */
public final class CastPlacement {

    private static final String PREFAB_BEAN_SUFFIX = "_prefab";
    private static final String RADIUS_PARAMETER = "radius";
    private static final float DEFAULT_BODY_RADIUS = 0.3f;

    // 두 몸의 반경 합에 이 비율을 곱한 거리보다 가까워야 겹친 것으로 본다. Unity client 가 같은 값을 쓴다.
    public static final float PLACEMENT_OVERLAP_RATIO = 0.6f;
    // 막힌 자리 둘레를 SNAP_RING_STEP 간격으로 SNAP_RING_COUNT 겹, 겹마다 SNAP_DIRECTIONS 방향을 본다.
    public static final float SNAP_RING_STEP = 0.25f;
    public static final int SNAP_RING_COUNT = 12;
    public static final int SNAP_DIRECTIONS = 16;

    private CastPlacement() {
    }

    public static boolean isBlocked(GameContext gameContext, Magic magic, Vector3 position) {
        if (!(magic instanceof ObjectSummoningMagic summoning) || summoning.summonsAirborne()) {
            return false;
        }

        float radius = bodyRadius(gameContext, summoning.summonedPrefab());
        Vector3 ground = position.grounded();
        if (touchesWater(gameContext.getTerrain(), ground, radius)) {
            return true;
        }
        for (GameObject other : gameContext.getActiveGameObjects()) {
            if (overlapsGroundBody(other, ground, radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 겨눈 자리가 막혔으면 사거리와 맵 안에서 가장 가까운 빈자리를 찾는다. 링은 안쪽부터, 각 링은 +x 방향
     * (각도 0)에서 시작해 반시계로 돌며, 처음 찾은 빈자리를 돌려준다. 없으면 비어 있다.
     */
    public static Optional<Vector3> resolveSpot(
            GameContext gameContext, Magic magic, Vector3 aim, Vector3 origin, double range) {
        if (!isBlocked(gameContext, magic, aim)) {
            return Optional.of(aim);
        }

        for (int ring = 1; ring <= SNAP_RING_COUNT; ring++) {
            float distance = ring * SNAP_RING_STEP;
            for (int i = 0; i < SNAP_DIRECTIONS; i++) {
                double angle = Math.PI * 2 * i / SNAP_DIRECTIONS;
                Vector3 candidate = aim.plus(
                        (float) (Math.cos(angle) * distance), 0, (float) (Math.sin(angle) * distance));
                if (!insideMap(candidate) || origin.distance(candidate) > range) {
                    continue;
                }
                if (!isBlocked(gameContext, magic, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    // 겨눈 자리에서 물 칸의 1 x 1 사각형까지 가장 가까운 점의 거리가 소환 반경 * 비율보다 작으면 막힌다.
    // 사각형 안을 겨누면 거리가 0 이라 막힌다. 다리 칸은 물이 아니므로 막지 않는다. Unity client 가 같은 식을 쓴다.
    private static boolean touchesWater(Terrain terrain, Vector3 ground, float radius) {
        if (terrain == null || terrain.isEmpty()) {
            return false;
        }
        return terrain.distanceToWater(ground) < radius * PLACEMENT_OVERLAP_RATIO;
    }

    private static boolean overlapsGroundBody(GameObject other, Vector3 ground, float radius) {
        if (!other.isActive() || other.isDestroyed()) {
            return false;
        }
        if (other.getPosition().getY() >= GameConfig.AERIAL_STANDARD_HEIGHT) {
            return false;
        }
        if (other.getComponents(Damageable.class).isEmpty()) {
            return false;
        }

        Optional<CircleCollider> body = other.getFirstCircleCollider(false);
        if (body.isEmpty()) {
            return false;
        }

        double distance = other.getPosition().grounded().distance(ground);
        return distance < (body.get().getRadius() + radius) * PLACEMENT_OVERLAP_RATIO;
    }

    private static float bodyRadius(GameContext gameContext, PrefabType prefabType) {
        String beanName = prefabType.getBeanName();
        String objectName = beanName.endsWith(PREFAB_BEAN_SUFFIX)
                ? beanName.substring(0, beanName.length() - PREFAB_BEAN_SUFFIX.length())
                : beanName;
        return (float) gameContext.getParameters()
                .getValueOrDefault(objectName, RADIUS_PARAMETER, DEFAULT_BODY_RADIUS);
    }

    private static boolean insideMap(Vector3 position) {
        return position.getX() >= MagicInputHandler.MAP_MIN_X && position.getX() <= MagicInputHandler.MAP_MAX_X
                && position.getZ() >= MagicInputHandler.MAP_MIN_Z && position.getZ() <= MagicInputHandler.MAP_MAX_Z;
    }
}
