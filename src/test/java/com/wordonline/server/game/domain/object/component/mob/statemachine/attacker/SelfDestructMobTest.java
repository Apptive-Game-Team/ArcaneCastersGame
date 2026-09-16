package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfDestructMobTest {

    @Test
    void explosionOnlyDamagesActiveEnemies() {
        GameContext gameContext = mock(GameContext.class);
        Physics physics = mock(Physics.class);
        when(gameContext.getPhysics()).thenReturn(physics);

        GameObject windSpirit = objectWithMob(Master.LeftPlayer, gameContext);
        GameObject ally = objectWithMob(Master.LeftPlayer, gameContext);
        GameObject enemy = objectWithMob(Master.RightPlayer, gameContext);
        GameObject destroyedEnemy = objectWithMob(Master.RightPlayer, gameContext);
        destroyedEnemy.setStatus(Status.Destroyed);
        when(physics.overlapSphereAll(any(GameObject.class), anyFloat()))
                .thenReturn(List.of(windSpirit, ally, enemy, destroyedEnemy));

        SelfDestructMob mob = new SelfDestructMob(windSpirit, 10, 1f, TargetMask.ANY.bit, 7, 1f, 2f, true);
        windSpirit.getComponents().add(mob);
        mob.onDeath();

        assertThat(damageOf(enemy)).isEqualTo(7);
        assertThat(damageOf(ally)).isZero();
        assertThat(damageOf(destroyedEnemy)).isZero();
        assertThat(damageOf(windSpirit)).isZero();
    }

    @Test
    void sameAltitudeTargetIsInterceptedInsteadOfProducingNaNPosition() {
        GameContext gameContext = mock(GameContext.class);
        Physics physics = mock(Physics.class);
        when(gameContext.getPhysics()).thenReturn(physics);

        GameObject windSpirit = new GameObject(Master.LeftPlayer, PrefabType.WindSpirit,
                new Vector3(4f, GameConfig.AERIAL_MOB_INIT_HEIGHT, 4f), gameContext);
        windSpirit.addCollider(new CircleCollider(windSpirit, 0.5f, false));
        GameObject enemy = new GameObject(Master.RightPlayer, PrefabType.BubbleSpirit,
                new Vector3(6f, GameConfig.AERIAL_MOB_INIT_HEIGHT, 4f), gameContext);
        enemy.addCollider(new CircleCollider(enemy, 0.5f, false));
        enemy.setStatus(Status.Idle);

        SelfDestructMob enemyMob = new SelfDestructMob(
                enemy, 10, 1f, TargetMask.AIR.bit, 0, 1f, 1f, true);
        enemy.getComponents().add(enemyMob);
        when(physics.overlapSphereAll(any(GameObject.class), anyFloat())).thenReturn(List.of(enemy));

        SelfDestructMob windSpiritMob = new SelfDestructMob(
                windSpirit, 10, 1f, TargetMask.AIR.bit, 7, 1f, 2f, true);
        windSpirit.getComponents().add(windSpiritMob);
        windSpiritMob.start();
        windSpiritMob.setState(windSpiritMob.new AttackingState(enemyMob));

        windSpiritMob.update();

        assertThat(windSpirit.getPosition().hasNaN()).isFalse();
        assertThat(windSpirit.getPosition().getX()).isEqualTo(6f);
        assertThat(enemyMob.getHp()).isLessThan(10);
        assertThat(windSpirit.isDestroyed()).isTrue();
    }

    @Test
    void explodesWhenAltitudeDropsToConfiguredThreshold() {
        GameContext gameContext = mock(GameContext.class);
        Physics physics = mock(Physics.class);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(gameContext.getGameSessionData()).thenReturn(
                new GameSessionData(mock(PlayerData.class), mock(PlayerData.class)));

        GameObject windSpirit = new GameObject(
                Master.LeftPlayer,
                PrefabType.WindSpirit,
                new Vector3(0f, 1.1f, 0f),
                gameContext);
        windSpirit.setStatus(Status.Idle);
        windSpirit.addCollider(new CircleCollider(windSpirit, 0.3f, false));
        GameObject enemy = objectWithMob(Master.RightPlayer, gameContext);
        when(physics.overlapSphereAll(any(GameObject.class), anyFloat()))
                .thenReturn(List.of(windSpirit, enemy));

        SelfDestructMob windSpiritMob = new SelfDestructMob(
                windSpirit, 10, 1f, TargetMask.AIR.bit, 7, 1f, 2f, true, 1f);
        windSpirit.getComponents().add(windSpiritMob);
        windSpiritMob.start();

        windSpiritMob.update();

        assertThat(windSpirit.isDestroyed()).isFalse();
        assertThat(damageOf(enemy)).isZero();

        windSpirit.setPosition(new Vector3(0f, 1f, 0f));
        windSpiritMob.update();

        assertThat(windSpirit.isDestroyed()).isTrue();
        assertThat(damageOf(enemy)).isEqualTo(7);
    }

    private int damageOf(GameObject gameObject) {
        return ((RecordingMob) gameObject.getComponent(Mob.class)).takenDamage;
    }

    private GameObject objectWithMob(Master master, GameContext gameContext) {
        GameObject gameObject = new GameObject(master, PrefabType.WindSpirit, Vector3.ZERO, gameContext);
        gameObject.setStatus(Status.Idle);
        gameObject.getComponents().add(new RecordingMob(gameObject));
        return gameObject;
    }

    private static class RecordingMob extends Mob {
        private int takenDamage;

        private RecordingMob(GameObject gameObject) {
            super(gameObject, 100, 1f);
        }

        @Override
        public void onDamaged(AttackInfo attackInfo) {
            takenDamage += attackInfo.getDamage();
        }

        @Override
        public void onDeath() {
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }
}
