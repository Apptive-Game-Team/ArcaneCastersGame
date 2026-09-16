# Physics & Collisions Reference

This document explains collision detection, impulse calculations, and Z-axis physics updates managed by the game server.

---

## The Physics Lifecycle

Physics calculations run on every tick inside [PhysicSystem.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/system/PhysicSystem.java#L33-L38):

```
1. calculateCollisions() -> Find overlapping colliders (CollisionChecker)
2. applyCollisionsResponses() -> Apply velocity impulses to RigidBodies
3. checkAndHandleCollisions() -> Fire onCollision()/onCollisionWithEnemy() triggers for Collidables
4. onUpdateEnd() -> Apply velocities to positions & clear collided pairs
```

---

## Detailed Phases

### Phase 1: Collision Detection (`calculateCollisions`)
- Scans all active, non-destroyed GameObjects.
- Filters objects that contain components implementing the `Collidable` interface.
- Executes [CollisionChecker.isColliding(a, b)](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/util/CollisionChecker.java) for each pair.
- If overlapping, the pair is registered in a `Set<Pair<GameObject>> collidedPairs` collection to prevent duplicate computations.

### Phase 2: Boundary/Overlap Resolution (`applyCollisionsResponses`)
- Processes the `collidedPairs`.
- Filters out triggers (colliders marked as `isTrigger`).
- Obtains the `RigidBody` components of both objects.
- Calculates separating velocities and normalized displacements.
- If the objects are moving toward each other, calculates a reflection impulse using the masses:
  `float totalInvMass = invMassA + invMassB;`
  `float impulseMag = - (1 + restitution) * separatingVelocity / totalInvMass;`
- Applies the impulse forces back to the velocities of both `RigidBody` components.

### Phase 3: Collision Event Dispatching (`checkAndHandleCollisions`)
- Evaluates the game events from the collision.
- Invokes `collidable.onCollision(other)` on all `Collidable` components of both GameObjects,
  for **every** pair, friendly ones included. E.g., this is where an electric shot overcharges
  the caster's own lightning summons through `AllyOverchargeProvider`.
- **Rule**: Skips the enemy dispatch for objects owned by the same player:
  `if (a.getMaster() == b.getMaster() && b.getMaster() != Master.None) return;`
- If the objects belong to opposing sides (or are neutral/None), also invokes:
  `collidable.onCollisionWithEnemy(other)` on all `Collidable` components of both GameObjects.
- E.g., this is where projectiles apply damage to `Damageable` components and destroy themselves.

### Phase 4: Velocity Integration (`onUpdateEnd`)
- Ticks final position updates:
  - **Horizontal Integration**: Triggers `RigidBody.applyVelocity()` to integrate the velocities into the 2D plane coordinates:
    `position = position.plus(velocity.multiply(deltaTime));`
  - **Z-Axis Integration**: Triggers `ZPhysics` to apply gravity accelerations or hovering forces along the vertical Z axis (e.g. for drop magics falling from the sky).
- Clears the `collidedPairs` set for the next tick.

---

## Spatial Queries

For area-of-effect spells (like explosions, totems, and meteor impacts), components can query the environment using the `Physics` interface spatial helpers:
- **`overlapSphereAll(gameObject, radius)`**: Returns all active GameObjects within a radial distance of the source object.
- **`raycast(origin, direction, range)`**: Traces a ray and returns the first intersecting target.
