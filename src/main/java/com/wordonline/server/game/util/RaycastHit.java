package com.wordonline.server.game.util;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * Unity 의 {@code RaycastHit} 처럼 ray 가 닿은 오브젝트와, ray 시작점에서 그 오브젝트의 표면에 닿은
 * 지점까지의 거리, 그 지점을 담는다.
 */
public record RaycastHit(GameObject gameObject, float distance, Vector3 point) {
}
