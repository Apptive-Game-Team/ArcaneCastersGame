package com.wordonline.server.game.service.system;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.prefab.implement.misc.WallPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.terrain.RiverWaterPrefabInitializer;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameTimer;
import com.wordonline.server.game.service.MagicInputHandler;
import com.wordonline.server.game.util.SimplePhysics;

/**
 * A real {@link GameContext} (real navigation, real session data, real physics system) with only
 * the client-facing object builder cut off. The terrain is set after the context exists, the way
 * {@code GameLoop.initializeLoop} does it, and the wall and the river cells are built by the
 * real prefab initializers. A frame is: every mob updates, then the physics system runs.
 */
final class RiverWorld {

    final GameContext context;
    final GameSessionData sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
    final PhysicSystem physicSystem = new PhysicSystem();
    final List<GameObject> water = new ArrayList<>();
    private final List<GameObject> mobs = new ArrayList<>();

    RiverWorld(Terrain terrain) {
        context = new GameContext(
                mock(GameTimer.class), sessionData, mock(Parameters.class),
                mock(MagicInputHandler.class), mock(DatabaseMagicParser.class)) {
            @Override
            public void createGameObject(GameObject gameObject) {
            }

            @Override
            public void updateGameObject(GameObject gameObject) {
            }
        };
        context.setPhysics(new SimplePhysics(sessionData.gameObjects));

        GameObject wall = add(new GameObject(Master.None, PrefabType.Wall, Vector3.ZERO, context));
        new WallPrefabInitializer(mock(Parameters.class)).initialize(wall);
        wall.flushComponents();

        context.setTerrain(terrain);
        for (Terrain.Cell cell : terrain.waterCells()) {
            GameObject water = add(new GameObject(Master.None, PrefabType.RiverWater, cell.center(), context));
            new RiverWaterPrefabInitializer().initialize(water);
            water.flushComponents();
            this.water.add(water);
        }
    }

    private GameObject add(GameObject gameObject) {
        gameObject.setStatus(Status.Idle);
        sessionData.gameObjects.add(gameObject);
        return gameObject;
    }

    /** A ground walker of the left side that heads for the nearest right side object. */
    GameObject walker(Vector3 position, float speed) {
        GameObject walker = new GameObject(Master.LeftPlayer, PrefabType.FireSlime, position, context);
        walker.addComponent(new RigidBody(walker, 2));
        walker.addCollider(new CircleCollider(walker, 0.5f, false));
        walker.addComponent(new Touchable(walker));
        walker.addComponent(new BehaviorMob(walker, 100, speed, TargetMask.GROUND.bit, 1f, 1f, target -> true));
        walker.flushComponents();
        mobs.add(walker);
        return add(walker);
    }

    /** A body that never moves by itself: a target for walkers, or a body for the tests to push. */
    GameObject body(Master master, Vector3 position) {
        return body(master, position, 2);
    }

    GameObject body(Master master, Vector3 position, int mass) {
        GameObject body = new GameObject(master, PrefabType.FireSlime, position, context);
        body.addComponent(new RigidBody(body, mass));
        body.addCollider(new CircleCollider(body, 0.5f, false));
        body.addComponent(new Touchable(body));
        body.addComponent(new DummyMob(body, 100));
        body.flushComponents();
        return add(body);
    }

    GameObject flyer(Vector3 position) {
        GameObject flyer = body(Master.LeftPlayer, position.withY(3f));
        return flyer;
    }

    /** A projectile: a trigger-free body with a collider and a collision handler but no hp. */
    GameObject projectile(Vector3 position) {
        GameObject projectile = new GameObject(Master.LeftPlayer, PrefabType.FireShot, position, context);
        projectile.addComponent(new RigidBody(projectile, 1));
        projectile.addCollider(new CircleCollider(projectile, 0.25f, false));
        projectile.addComponent(new Touchable(projectile));
        projectile.flushComponents();
        return add(projectile);
    }

    /** Whether the body's collider touches any side of any river cell. */
    boolean touchesWater(GameObject body) {
        for (GameObject cell : water) {
            if (com.wordonline.server.game.util.CollisionChecker.isColliding(cell, body)) {
                return true;
            }
        }
        return false;
    }

    void tick() {
        context.incrementFrameNum();
        for (GameObject mob : new ArrayList<>(mobs)) {
            for (Component component : mob.getComponents()) {
                if (component instanceof BehaviorMob) {
                    component.update();
                }
            }
        }
        physicSystem.update(context);
    }

    void tick(int frames) {
        for (int i = 0; i < frames; i++) {
            tick();
        }
    }

    /** Counts the collision callbacks, so a test can see whether a pair was reported at all. */
    static final class Touchable extends Component implements Collidable {
        int touches;

        Touchable(GameObject gameObject) {
            super(gameObject);
        }

        @Override
        public void onCollisionWithEnemy(GameObject otherObject) {
            touches++;
        }

        @Override
        public void onCollision(GameObject otherObject) {
            touches++;
        }

        @Override
        public void start() {
        }

        @Override
        public void update() {
        }

        @Override
        public void onDestroy() {
        }
    }
}
