package com.wordonline.server.game.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;

class PhysicSystemRiverTest {

    private final GameContext gameContext = mock(GameContext.class);
    private final PhysicSystem physicSystem = new PhysicSystem();

    @Test
    void aGroundBodyPushedIntoTheWaterIsPutBackOnTheNearBank() {
        GameObject unit = unit(new Vector3(7.9f, 0f, 5f));
        RigidBody rigidBody = unit.getComponent(RigidBody.class);
        // 0.1초 동안 +x 로 3 만큼의 속도: 7.9 에서 8.2 로 물 칸에 들어간다
        rigidBody.addVelocity(new Vector3(3f, 0f, 0f));

        step(unit);

        assertThat(Terrain.RIVER.isWaterAt(unit.getPosition())).isFalse();
        assertThat(unit.getPosition().getX()).isLessThan(8f);
        assertThat(unit.getPosition().getZ()).isEqualTo(5f);
        assertThat(rigidBody.getVelocity().getX()).isZero();
    }

    @Test
    void aGroundBodyPulledStraightIntoTheWaterByAPositionWriteIsPutBackToo() {
        GameObject unit = unit(new Vector3(6f, 0f, 5f));
        unit.setPosition(new Vector3(9f, 0f, 5.2f));

        step(unit);

        assertThat(Terrain.RIVER.isWaterAt(unit.getPosition())).isFalse();
    }

    @Test
    void aGroundBodyOnABridgeIsNotMoved() {
        GameObject unit = unit(new Vector3(8.5f, 0f, 2.5f));
        Vector3 before = new Vector3(unit.getPosition());

        step(unit);

        assertThat(unit.getPosition()).isEqualTo(before);
    }

    @Test
    void aFlyerOverTheWaterIsUntouched() {
        GameObject flyer = unit(new Vector3(9f, 3f, 5f));
        Vector3 before = new Vector3(flyer.getPosition());

        step(flyer);

        assertThat(flyer.getPosition()).isEqualTo(before);
    }

    @Test
    void aProjectileADropAndAnExplosionOverTheWaterAreUntouched() {
        // 쏘기, 떨구기, 폭발은 Damageable 이 없고 RigidBody 도 없다
        GameObject shot = bare(PrefabType.FireShot, new Vector3(9f, 0.5f, 5f));
        GameObject drop = bare(PrefabType.FireExplode, new Vector3(9f, 0f, 5f));
        Vector3 shotBefore = new Vector3(shot.getPosition());
        Vector3 dropBefore = new Vector3(drop.getPosition());
        when(gameContext.getTerrain()).thenReturn(Terrain.RIVER);
        when(gameContext.getActiveGameObjects()).thenReturn(List.of(shot, drop));

        physicSystem.update(gameContext);

        assertThat(shot.getPosition()).isEqualTo(shotBefore);
        assertThat(drop.getPosition()).isEqualTo(dropBefore);
    }

    @Test
    void aDyingBodyFallingOverTheWaterIsLeftAlone() {
        GameObject unit = unit(new Vector3(9f, 0f, 5f));
        unit.setStatus(Status.Dying);
        Vector3 before = new Vector3(unit.getPosition());

        step(unit);

        assertThat(unit.getPosition()).isEqualTo(before);
    }

    @Test
    void theOpenArenaNeverMovesAnything() {
        GameObject unit = unit(new Vector3(9f, 0f, 5f));
        Vector3 before = new Vector3(unit.getPosition());
        when(gameContext.getTerrain()).thenReturn(Terrain.NONE);

        physicSystem.update(gameContext);

        assertThat(unit.getPosition()).isEqualTo(before);
    }

    private void step(GameObject... objects) {
        when(gameContext.getTerrain()).thenReturn(Terrain.RIVER);
        when(gameContext.getDeltaTime()).thenReturn(0.1f);
        when(gameContext.getActiveGameObjects()).thenReturn(List.of(objects));

        physicSystem.update(gameContext);
    }

    private GameObject unit(Vector3 position) {
        GameObject unit = new GameObject(Master.LeftPlayer, PrefabType.FireSlime, position, gameContext);
        unit.addComponent(new RigidBody(unit, 2));
        unit.addComponent(new HitPoints(unit));
        unit.addCollider(new CircleCollider(unit, 0.5f, false));
        unit.flushComponents();
        unit.setStatus(Status.Idle);
        return unit;
    }

    private GameObject bare(PrefabType type, Vector3 position) {
        GameObject object = new GameObject(Master.LeftPlayer, type, position, gameContext);
        object.flushComponents();
        object.setStatus(Status.Idle);
        return object;
    }

    /** A body with hp: all the physics step needs to call it a unit. */
    private static class HitPoints extends Component implements Damageable {

        HitPoints(GameObject gameObject) {
            super(gameObject);
        }

        @Override
        public void onDamaged(AttackInfo attackInfo) {
        }

        @Override
        public void onDamaged(AttackInfo attackInfo, float delay) {
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
