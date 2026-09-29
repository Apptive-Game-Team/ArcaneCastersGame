package com.wordonline.server.game.service;

import java.util.Optional;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * 유닛과 건물을 놓을 자리가 이미 땅 위의 다른 몸과 겹치는지 본다. 공중에 나타나는 소환은 무엇과도
 * 겹쳐 놓을 수 있고, 공중에 떠 있는 몸도 장애물로 치지 않는다. 쏘기·떨구기·폭발처럼 몸을 남기지
 * 않는 마법은 검사하지 않는다.
 */
public final class CastPlacement {

    private static final String PREFAB_BEAN_SUFFIX = "_prefab";
    private static final String RADIUS_PARAMETER = "radius";
    private static final float DEFAULT_BODY_RADIUS = 0.3f;

    // 봇이 고른 자리가 막혔을 때 조금씩 비켜 볼 거리와 방향 수
    private static final float[] BOT_NUDGE_DISTANCES = {0.5f, 1f, 1.5f, 2f};
    private static final int BOT_NUDGE_DIRECTIONS = 8;

    private CastPlacement() {
    }

    public static boolean isBlocked(GameContext gameContext, Magic magic, Vector3 position) {
        if (!(magic instanceof ObjectSummoningMagic summoning) || summoning.summonsAirborne()) {
            return false;
        }

        float radius = bodyRadius(gameContext, summoning.summonedPrefab());
        Vector3 ground = position.grounded();
        for (GameObject other : gameContext.getActiveGameObjects()) {
            if (overlapsGroundBody(other, ground, radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 봇이 고른 자리가 막혔으면 사거리 안에서 가까운 빈자리를 찾는다. 사람은 자리를 다시 고르면 되지만,
     * 봇은 같은 자리를 계속 골라 카드를 못 쓰게 된다.
     */
    public static Optional<Vector3> findFreeSpotForBot(
            GameContext gameContext, Magic magic, Vector3 position, Vector3 origin, double range) {
        if (!isBlocked(gameContext, magic, position)) {
            return Optional.of(position);
        }

        for (float distance : BOT_NUDGE_DISTANCES) {
            for (int i = 0; i < BOT_NUDGE_DIRECTIONS; i++) {
                double angle = Math.PI * 2 * i / BOT_NUDGE_DIRECTIONS;
                Vector3 candidate = position.plus(
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
        return distance < body.get().getRadius() + radius;
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
