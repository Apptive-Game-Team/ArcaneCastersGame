package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhysicSystemFriendlyCollisionTest {

    private static class CollisionRecorder extends Component implements Collidable {

        private final List<GameObject> everyCollision = new ArrayList<>();
        private final List<GameObject> enemyCollisions = new ArrayList<>();

        private CollisionRecorder(GameObject gameObject) {
            super(gameObject);
        }

        @Override
        public void onCollision(GameObject otherObject) {
            everyCollision.add(otherObject);
        }

        @Override
        public void onCollisionWithEnemy(GameObject otherObject) {
            enemyCollisions.add(otherObject);
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

    private final GameContext gameContext = mock(GameContext.class);

    private CollisionRecorder overlappingObject(Master master, List<GameObject> objects) {
        GameObject gameObject = new GameObject(master, PrefabType.ElectricSlime, Vector3.ZERO, gameContext);
        CollisionRecorder recorder = new CollisionRecorder(gameObject);
        gameObject.addComponent(recorder);
        gameObject.addCollider(new CircleCollider(gameObject, 1f, true));
        gameObject.flushComponents();
        objects.add(gameObject);
        return recorder;
    }

    @Test
    void friendlyPairOnlyReceivesTheAllCollisionCallback() {
        List<GameObject> objects = new ArrayList<>();
        CollisionRecorder a = overlappingObject(Master.LeftPlayer, objects);
        CollisionRecorder b = overlappingObject(Master.LeftPlayer, objects);
        when(gameContext.getActiveGameObjects()).thenReturn(objects);

        new PhysicSystem().update(gameContext);

        assertThat(a.everyCollision).containsExactly(b.getGameObject());
        assertThat(b.everyCollision).containsExactly(a.getGameObject());
        assertThat(a.enemyCollisions).isEmpty();
        assertThat(b.enemyCollisions).isEmpty();
    }

    @Test
    void enemyPairReceivesBothCallbacks() {
        List<GameObject> objects = new ArrayList<>();
        CollisionRecorder a = overlappingObject(Master.LeftPlayer, objects);
        CollisionRecorder b = overlappingObject(Master.RightPlayer, objects);
        when(gameContext.getActiveGameObjects()).thenReturn(objects);

        new PhysicSystem().update(gameContext);

        assertThat(a.everyCollision).containsExactly(b.getGameObject());
        assertThat(a.enemyCollisions).containsExactly(b.getGameObject());
        assertThat(b.everyCollision).containsExactly(a.getGameObject());
        assertThat(b.enemyCollisions).containsExactly(a.getGameObject());
    }
}
