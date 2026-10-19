package com.wordonline.server.game.util;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;

import java.util.List;

public interface Physics {

    default List<GameObject> overlapSphereAll(GameObject object, float radius) {
        return overlapSphereAll(object.getPosition(), radius);
    }

    List<GameObject> overlapSphereAll(Vector3 position, float radius);

    /**
     * Unity 의 {@code Physics.SphereCastAll} 처럼, 반지름 {@code radius} 인 원을 {@code origin} 에서
     * {@code direction} 으로 {@code maxDistance} 만큼 밀었을 때 닿는 오브젝트를 모두 돌려준다. 원형 collider 만
     * 보고 높이는 무시한다. {@code origin} 보다 뒤에 중심이 있는 오브젝트는 넣지 않는다.
     *
     * <p>Unity 와 달리 결과는 {@code direction} 방향의 거리가 가까운 것부터 정렬되어 있다.
     */
    List<GameObject> sphereCastAll(Vector3 origin, float radius, Vector3 direction, float maxDistance);
    List<GameObject> overlapBoxAll(Vector3 center, Vector3 size);
    GameObject raycast(GameObject object, Vector3 direction, float distance);
}
