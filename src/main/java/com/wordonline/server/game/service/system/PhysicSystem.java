package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.Collider;
import com.wordonline.server.game.domain.object.component.physic.EdgeCollider;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.Pair;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.util.CollisionChecker;
import com.wordonline.server.game.util.CollisionSystem;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class PhysicSystem implements CollisionSystem, GameSystem {

    private static final float SAME_PLACE_THRESHOLD = 1e-6f;

    private final Set<Pair<GameObject>> collidedPairs = new HashSet<>();
    // Scratch list reused across frames: the broad phase fills it once per call with the
    // objects that can actually take part in a collision, so the pair loop never has to
    // re-derive per-object data. PhysicSystem is prototype-scoped and only ever touched by
    // its own single-threaded game loop, same as collidedPairs above.
    private final List<GameObject> collisionCandidates = new ArrayList<>();

    @Override
    public void update(GameContext gameContext) {
        List<GameObject> gameObjects = gameContext.getActiveGameObjects();
        handleCollisions(gameObjects);
        checkAndHandleCollisions(gameObjects);
        // deliberately re-read: collision handlers, and the out-of-bounds destroy inside
        // setPosition, can remove objects mid-frame, so the velocity pass wants the fresher
        // list rather than the one the broad phase started from
        onUpdateEnd(gameContext.getActiveGameObjects());
    }

    private void handleCollisions(List<GameObject> gameObjects) {
        calculateCollisions(gameObjects);
        applyCollisionsResponses();
    }

    private void calculateCollisions(List<GameObject> gameObjects) {
        collectCollisionCandidates(gameObjects);

        for (int i = 0; i < collisionCandidates.size(); i++) {
            GameObject a = collisionCandidates.get(i);

            for (int j = i + 1; j < collisionCandidates.size(); j++) {
                GameObject b = collisionCandidates.get(j);

                if (CollisionChecker.isColliding(a, b)) {
                    collidedPairs.add(new Pair<>(a, b));
                }
            }
        }
    }

    // Both predicates are pure and nothing in the pair loop below changes an object's
    // status or components, so testing each object once is equivalent to testing it again
    // for every pair it appears in. The candidate list keeps the source order, so pairs are
    // still formed with the same (a, b) orientation as before.
    private void collectCollisionCandidates(List<GameObject> gameObjects) {
        collisionCandidates.clear();
        for (int i = 0; i < gameObjects.size(); i++) {
            GameObject gameObject = gameObjects.get(i);
            if (!isCollidable(gameObject)) continue;
            if (gameObject.getComponents(Collidable.class).isEmpty()) continue;
            collisionCandidates.add(gameObject);
        }
    }

    // a dying object only falls down, it neither pushes nor gets hit by anything
    private boolean isCollidable(GameObject gameObject) {
        return !gameObject.isDestroyed() && !gameObject.isDying();
    }

    private void applyCollisionsResponses() {
        collidedPairs.forEach(
                gameObjectPair -> {
                    GameObject a = gameObjectPair.a();
                    GameObject b = gameObjectPair.b();

                    RigidBody rigidBodyA = a.getComponent(RigidBody.class);
                    RigidBody rigidBodyB = b.getComponent(RigidBody.class);

                    gameObjectPair.a().getColliders().stream().filter(Collider::isNotTrigger).forEach(
                            colliderA -> {
                                gameObjectPair.b().getColliders().stream().filter(Collider::isNotTrigger).forEach(
                                    colliderB -> {

                                        if (!colliderA.isCollidingWish(colliderB)) {
                                            return;
                                        }

                                        float invMassA = colliderA.getInvMass();
                                        float invMassB = colliderB.getInvMass();

                                        Vector3 normal = getNormalizedDisplacement(colliderA, colliderB);

                                        if (normal == null) {
                                            return;
                                        }

                                        Vector3 relativeVelocity = colliderA.getVelocity().subtract(colliderB.getVelocity());
                                        float separatingVelocity = relativeVelocity.dot(normal);

                                        // 이미 멀어지는 중이면 무시
                                        if (separatingVelocity > 0) return;

                                        // 반사량 계산
                                        float restitution = 1.0f; // 탄성 계수
                                        float totalInvMass = invMassA + invMassB;
                                        if (totalInvMass == 0) return;

                                        float impulseMag = - (1 + restitution) * separatingVelocity /
                                              totalInvMass;
                                        // Dynamic bodies keep the existing capped response so crowds do not
                                        // explode apart. Map edges are immovable walls, however, and need the
                                        // full impulse to cancel fast movement such as a coward's panic flee.
                                        if (!(colliderA instanceof EdgeCollider || colliderB instanceof EdgeCollider)) {
                                            impulseMag = Math.clamp(impulseMag, -1.0f, 1.0f);
                                        }

                                        Vector3 impulse = normal.multiply(impulseMag);
                                        if (invMassA > 0) {
                                          rigidBodyA.addVelocity(impulse.multiply(invMassA));
                                        }
                                        if (invMassB > 0) {
                                          rigidBodyB.addVelocity(impulse.multiply(-invMassB));
                                        }
                                    }
                                );
                            }
                    );
                }
        );
    }

    private Vector3 getNormalizedDisplacement(Collider a, Collider b) {
        Vector3 displacement = a.getDisplacement(b);

        if (displacement == null) {
            return null;
        }

        if (Math.abs(displacement.getZ()) <= SAME_PLACE_THRESHOLD
                && Math.abs(displacement.getX()) <= SAME_PLACE_THRESHOLD) {
            return Vector3.randomUnitVector();
        }

        return displacement.normalize();
    }

    @Override
    public void checkAndHandleCollisions(List<GameObject> gameObjects) {
        collidedPairs.forEach(
                gameObjectPair -> {
                    GameObject a = gameObjectPair.a();
                    GameObject b = gameObjectPair.b();

                    if (a.isDestroyed() || b.isDestroyed()) {
                        return;
                    }

                    a.getComponents(Collidable.class).forEach(collidable -> collidable.onCollision(b));
                    b.getComponents(Collidable.class).forEach(collidable -> collidable.onCollision(a));

                    if (isSameSide(a, b)) {
                        return;
                    }

                    // a friendly reaction above may have destroyed one of the two objects
                    if (a.isDestroyed() || b.isDestroyed()) {
                        return;
                    }

                    a.getComponents(Collidable.class).forEach(collidable -> collidable.onCollisionWithEnemy(b));
                    b.getComponents(Collidable.class).forEach(collidable -> collidable.onCollisionWithEnemy(a));
                }
        );
    }

    private boolean isSameSide(GameObject a, GameObject b) {
        return a.getMaster() == b.getMaster() && b.getMaster() != Master.None;
    }

    // apply rigidbody velocity and clear velocity
    private void onUpdateEnd(List<GameObject> gameObjects) {
        gameObjects.forEach(
                gameObject -> {
                    handleApplyingRigidBody(gameObject);
                    handleApplyingZPhysics(gameObject);
                }
        );
        collidedPairs.clear();
    }

    private void handleApplyingRigidBody(GameObject gameObject) {
        RigidBody rigidBody = gameObject.getComponent(RigidBody.class);
        if (rigidBody == null) {
            return;
        }
        rigidBody.applyVelocity();
    }

    private void handleApplyingZPhysics(GameObject gameObject) {
        ZPhysics zPhysics = gameObject.getComponent(ZPhysics.class);
        if (zPhysics == null) {
            return;
        }
        if(zPhysics.canHover()) zPhysics.applyHover();
        else zPhysics.applyZForce();
    }
}
