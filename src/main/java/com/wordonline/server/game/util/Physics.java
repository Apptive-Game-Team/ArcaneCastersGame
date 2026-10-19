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
     * Unity 의 {@code Physics.RaycastAll} 처럼, {@code origin} 에서 {@code direction} 으로 {@code maxDistance}
     * 만큼 뻗은 두께 없는 ray 에 닿는 오브젝트를 모두 돌려준다. 원형 collider 를 그 반지름의 구로 보고
     * 높이까지 3차원으로 판정한다. ray 가 안에서 시작하는 collider 는 넣지 않는다.
     *
     * <p>Unity 와 달리 결과는 {@link RaycastHit#distance()} 가 가까운 것부터 정렬되어 있다.
     */
    List<RaycastHit> raycastAll(Vector3 origin, Vector3 direction, float maxDistance);
    List<GameObject> overlapBoxAll(Vector3 center, Vector3 size);
    GameObject raycast(GameObject object, Vector3 direction, float distance);
}
