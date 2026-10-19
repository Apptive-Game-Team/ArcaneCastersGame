package com.wordonline.server.game.util;

import static org.assertj.core.api.Assertions.assertThat;
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

    private final GameContext context = mock(GameContext.class);

    @Test
    void sphereCastAllReturnsObjectsOnTheLineOrderedByDistanceAlongIt() {
        GameObject far = circle(new Vector3(8f, 0f, 0f), 0.5f);
        GameObject offCenter = circle(new Vector3(5f, 0f, 1.2f), 0.5f);
        GameObject onLine = circle(new Vector3(5.1f, 0f, 0f), 0.5f);
        SimplePhysics physics = new SimplePhysics(List.of(far, onLine, offCenter));

        List<GameObject> hits = physics.sphereCastAll(Vector3.ZERO, 0.75f, new Vector3(1f, 0f, 0f), 18f);

        // 직선 거리는 onLine(5.1) 이 offCenter(약 5.14) 보다 가깝지만, 직선 방향의 거리로는 offCenter(5) 가 앞이다.
        assertThat(hits).containsExactly(offCenter, onLine, far);
    }

    @Test
    void sphereCastAllSkipsObjectsBehindBesideOrBeyondTheLine() {
        GameObject behind = circle(new Vector3(-0.5f, 0f, 0f), 0.5f);
        GameObject beside = circle(new Vector3(5f, 0f, 1.3f), 0.5f);
        GameObject beyond = circle(new Vector3(18.6f, 0f, 0f), 0.5f);
        GameObject edgeOfRange = circle(new Vector3(18.4f, 0f, 0f), 0.5f);
        SimplePhysics physics = new SimplePhysics(List.of(behind, beside, beyond, edgeOfRange));

        List<GameObject> hits = physics.sphereCastAll(Vector3.ZERO, 0.75f, new Vector3(1f, 0f, 0f), 18f);

        assertThat(hits).containsExactly(edgeOfRange);
    }

    @Test
    void sphereCastAllIgnoresHeightAndObjectsWithoutACollider() {
        GameObject flying = circle(new Vector3(4f, 3f, 0f), 0.5f);
        GameObject noCollider = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(2f, 0f, 0f), context);
        SimplePhysics physics = new SimplePhysics(List.of(flying, noCollider));

        List<GameObject> hits = physics.sphereCastAll(new Vector3(0f, 1f, 0f), 0.75f, new Vector3(1f, 2f, 0f), 18f);

        assertThat(hits).containsExactly(flying);
    }

    private GameObject circle(Vector3 position, float radius) {
        GameObject object = new GameObject(Master.RightPlayer, PrefabType.MiniRock, position, context);
        object.addCollider(new CircleCollider(object, radius, false));
        return object;
    }
}
