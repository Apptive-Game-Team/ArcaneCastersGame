package com.wordonline.server.game.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

class SimplePhysicsTest {

    private static final Vector3 RIGHT = new Vector3(1f, 0f, 0f);

    private final GameContext context = mock(GameContext.class);

    @Test
    void raycastAllReturnsHitsOrderedByTheDistanceToTheBodySurface() {
        GameObject far = circle(new Vector3(8f, 0f, 0f), 0.5f);
        // 중심까지의 직선 거리는 offCenter(약 5.02) 가 onLine(5.2) 보다 가깝다.
        // ray 가 몸에 닿는 거리는 onLine(4.7) 이 offCenter(약 4.78) 보다 가깝다.
        GameObject offCenter = circle(new Vector3(5f, 0f, 0.45f), 0.5f);
        GameObject onLine = circle(new Vector3(5.2f, 0f, 0f), 0.5f);
        SimplePhysics physics = new SimplePhysics(List.of(far, offCenter, onLine));

        List<RaycastHit> hits = physics.raycastAll(Vector3.ZERO, RIGHT, 18f);

        assertThat(hits).extracting(RaycastHit::gameObject).containsExactly(onLine, offCenter, far);
        assertThat(hits.getFirst().distance()).isCloseTo(4.7f, within(1e-4f));
        assertThat(hits.getFirst().point().getX()).isCloseTo(4.7f, within(1e-4f));
    }

    @Test
    void raycastAllSkipsObjectsBehindBesideOrBeyondTheRay() {
        GameObject behind = circle(new Vector3(-0.5f, 0f, 0f), 0.5f);
        GameObject beside = circle(new Vector3(5f, 0f, 0.6f), 0.5f);
        GameObject beyond = circle(new Vector3(18.6f, 0f, 0f), 0.5f);
        GameObject edgeOfRange = circle(new Vector3(18.4f, 0f, 0f), 0.5f);
        SimplePhysics physics = new SimplePhysics(List.of(behind, beside, beyond, edgeOfRange));

        List<RaycastHit> hits = physics.raycastAll(Vector3.ZERO, RIGHT, 18f);

        assertThat(hits).extracting(RaycastHit::gameObject).containsExactly(edgeOfRange);
    }

    @Test
    void raycastAllTakesHeightIntoAccount() {
        GameObject flying = circle(new Vector3(4f, 2f, 0f), 0.5f);
        GameObject grounded = circle(new Vector3(6f, 0f, 0f), 0.5f);
        SimplePhysics physics = new SimplePhysics(List.of(flying, grounded));

        assertThat(physics.raycastAll(Vector3.ZERO, RIGHT, 18f))
                .extracting(RaycastHit::gameObject)
                .containsExactly(grounded);
        assertThat(physics.raycastAll(new Vector3(0f, 2f, 0f), RIGHT, 18f))
                .extracting(RaycastHit::gameObject)
                .containsExactly(flying);
    }

    @Test
    void raycastAllSkipsObjectsWithoutACollider() {
        GameObject noCollider = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(2f, 0f, 0f), context);
        SimplePhysics physics = new SimplePhysics(List.of(noCollider));

        assertThat(physics.raycastAll(Vector3.ZERO, RIGHT, 18f)).isEmpty();
    }

    private GameObject circle(Vector3 position, float radius) {
        GameObject object = new GameObject(Master.RightPlayer, PrefabType.MiniRock, position, context);
        object.addCollider(new CircleCollider(object, radius, false));
        return object;
    }
}
