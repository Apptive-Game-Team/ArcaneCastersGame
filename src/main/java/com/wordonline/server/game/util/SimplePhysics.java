package com.wordonline.server.game.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.CircleCollidingHelper;
import com.wordonline.server.game.domain.object.component.physic.Collider;

public class SimplePhysics implements Physics {
    private final List<GameObject> gameObjects;

    public SimplePhysics(List<GameObject> gameObjects) {
        this.gameObjects = gameObjects;
    }

    @Override
    public List<GameObject> overlapSphereAll(Vector3 originPosition, float radius) {
        List<GameObject> result = new ArrayList<>();
        CircleCollidingHelper circle = new CircleCollidingHelper(radius, originPosition);

        for (GameObject other : gameObjects) {
            List<Collider> colliders = other.getColliders();
            if (colliders == null || colliders.isEmpty()) continue;

            if(circle.isCollidingWish(colliders.getFirst())) result.add(other);
        }
        return result;
    }

    @Override
    public List<GameObject> sphereCastAll(Vector3 origin, float radius, Vector3 direction, float maxDistance) {
        Vector3 groundOrigin = origin.grounded();
        Vector3 dirNorm = direction.grounded().normalize();
        if (dirNorm.equals(Vector3.ZERO)) {
            return List.of();
        }

        List<GameObject> result = new ArrayList<>();
        for (GameObject other : gameObjects) {
            List<Collider> colliders = other.getColliders();
            if (colliders == null || colliders.isEmpty()
                    || !(colliders.getFirst() instanceof CircleCollider circle)) continue;

            Vector3 offset = other.getPosition().grounded().subtract(groundOrigin);
            float along = offset.dot(dirNorm);
            if (along < 0f || along > maxDistance + circle.getRadius()) continue;

            double lateral = offset.distance(dirNorm.multiply(along));
            if (lateral <= radius + circle.getRadius()) result.add(other);
        }

        result.sort(Comparator.comparingDouble(other ->
                other.getPosition().grounded().subtract(groundOrigin).dot(dirNorm)));
        return result;
    }

    @Override
    public List<GameObject> overlapBoxAll(Vector3 center, Vector3 size) {
        List<GameObject> result = new ArrayList<>();
        float halfX = size.getX() / 2;
        float halfY = size.getY() / 2;
        float halfZ = size.getZ() / 2;

        float minX = center.getX() - halfX;
        float maxX = center.getX() + halfX;
        float minY = center.getY() - halfY;
        float maxY = center.getY() + halfY;
        float minZ = center.getZ() - halfZ;
        float maxZ = center.getZ() + halfZ;

        for (GameObject other : gameObjects) {
            Vector3 pos = other.getPosition();
            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public GameObject raycast(GameObject object, Vector3 direction, float distance) {
        Vector3 origin = object.getPosition();
        Vector3 dirNorm = direction.grounded().normalize();

        GameObject closest = null;
        double closestDist = distance + 1;

        for (GameObject other : gameObjects) {
            if (other == object) continue;

            Vector3 toOther = other.getPosition().subtract(origin).grounded();
            double projection = toOther.dot(dirNorm);

            if (projection < 0 || projection > distance) continue;

            if (CollisionChecker.isColliding(object, other) && projection < closestDist) {
                closest = other;
                closestDist = projection;
            }
        }

        return closest;
    }
}
    
