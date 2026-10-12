package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.FlowFieldNavigation;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ProjectileDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.util.SimplePhysics;

class PveBrickGolemMobTest {

    private static final float DELTA_TIME = 0.1f;
    private static final int DAMAGE = 30;
    private static final float ATTACK_INTERVAL = 4f;
    private static final float ATTACK_RANGE = 12f;
    private static final float EFFECT_RADIUS = 2f;
    private static final int SUB_DAMAGE = 12;
    private static final float SUB_ATTACK_INTERVAL = 100f;
    private static final float SUB_ATTACK_RANGE = 4f;
    private static final Vector3 GOLEM_POSITION = new Vector3(10f, 0f, 5f);

    private final List<ProjectileDto> sentProjectiles = new ArrayList<>();

    private GameContext gameContext;
    private GameSessionData sessionData;
    private ObjectsInfoDtoBuilder objectsInfoDtoBuilder;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        objectsInfoDtoBuilder = new ObjectsInfoDtoBuilder(gameContext);
        SimplePhysics physics = new SimplePhysics(sessionData.gameObjects);
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(DELTA_TIME);
        when(gameContext.getNavigation()).thenReturn(new FlowFieldNavigation(() -> 0, List::of));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(objectsInfoDtoBuilder);
        when(gameContext.overlapSphereAll(any(Vector3.class), anyFloat()))
                .thenAnswer(invocation -> physics.overlapSphereAll((Vector3) invocation.getArgument(0), (float) invocation.getArgument(1)));
    }

    @Test
    void slamWarnsAtOncePinsTheSpotAndDamagesOnlyInsideTheRadiusAfterOneSecond() {
        PveBrickGolemMob golem = golem(SUB_ATTACK_INTERVAL);
        // The target walks off after the warning; the second unit stands next to the marked spot.
        GameObject target = victim(Master.LeftPlayer, new Vector3(4f, 0f, 5f));
        GameObject neighbour = victim(Master.LeftPlayer, new Vector3(4f, 0f, 6.5f));
        GameObject faraway = victim(Master.LeftPlayer, new Vector3(4f, 0f, 9f));

        tickUntil(golem, () -> sentTypes().contains("BrickGolemSlamWarning"));

        ProjectileDto warning = projectile("BrickGolemSlamWarning");
        assertThat(warning.getDuration()).isEqualTo(1.0f);
        assertThat(((PositionProjectileTarget) warning.getStart()).getX()).isEqualTo(4f);
        assertThat(((PositionProjectileTarget) warning.getEnd()).getZ()).isEqualTo(5f);
        assertThat(golem.getGameObject().getStatus()).isEqualTo(Status.Attack);
        assertThat(hp(target)).isEqualTo(1000);

        target.setPosition(new Vector3(8f, 0f, 5f));
        tick(golem, 5);
        assertThat(hp(neighbour)).isEqualTo(1000);

        tick(golem, 6);

        assertThat(hp(target)).isEqualTo(1000);
        assertThat(hp(neighbour)).isEqualTo(1000 - DAMAGE);
        assertThat(hp(faraway)).isEqualTo(1000);
        ProjectileDto slam = projectile("BrickGolemSlam");
        assertThat(slam.getDuration()).isEqualTo(0.3f);
        assertThat(((PositionProjectileTarget) slam.getStart()).getX()).isEqualTo(GOLEM_POSITION.getX());
        assertThat(((PositionProjectileTarget) slam.getEnd()).getX()).isEqualTo(4f);
    }

    @Test
    void slamDoesNotHurtItsOwnSide() {
        PveBrickGolemMob golem = golem(SUB_ATTACK_INTERVAL);
        victim(Master.LeftPlayer, new Vector3(4f, 0f, 5f));
        GameObject ally = victim(Master.RightPlayer, new Vector3(4f, 0f, 5.5f));

        tick(golem, 60);

        assertThat(hp(ally)).isEqualTo(1000);
    }

    @Test
    void sweepHitsANearEnemyAndKnocksItAwayFromTheGolem() {
        PveBrickGolemMob golem = golem(0.1f);
        GameObject near = victim(Master.LeftPlayer, new Vector3(7f, 0f, 5f));
        RecordingReceiver receiver = receiverOn(near);

        tickUntil(golem, () -> sentTypes().contains("BrickGolemSweep"));

        ProjectileDto sweep = projectile("BrickGolemSweep");
        assertThat(sweep.getDuration()).isEqualTo(0.4f);
        assertThat(((PositionProjectileTarget) sweep.getEnd()).getX()).isEqualTo(GOLEM_POSITION.getX() - SUB_ATTACK_RANGE);
        assertThat(hp(near)).isEqualTo(1000 - SUB_DAMAGE);
        assertThat(receiver.effect).isEqualTo(Effect.Knockback);
        assertThat(receiver.direction.getX()).isLessThan(0f);
        assertThat(receiver.direction.getZ()).isZero();
        assertThat(receiver.strength).isEqualTo(PveBrickGolemMob.SWEEP_KNOCKBACK_STRENGTH);
        assertThat(golem.getGameObject().getStatus()).isEqualTo(Status.Attack);
    }

    @Test
    void sweepMissesAnEnemyBehindTheGolem() {
        PveBrickGolemMob golem = golem(0.1f);
        GameObject near = victim(Master.LeftPlayer, new Vector3(7f, 0f, 5f));
        GameObject behind = victim(Master.LeftPlayer, new Vector3(13f, 0f, 5f));
        RecordingReceiver behindReceiver = receiverOn(behind);

        tickUntil(golem, () -> hp(near) < 1000);

        assertThat(hp(near)).isEqualTo(1000 - SUB_DAMAGE);
        assertThat(hp(behind)).isEqualTo(1000);
        assertThat(behindReceiver.effect).isNull();
    }

    @Test
    void sweepWaitsWhileNoEnemyIsWithinItsRange() {
        PveBrickGolemMob golem = golem(0.1f);
        GameObject far = victim(Master.LeftPlayer, new Vector3(4f, 0f, 5f));

        tick(golem, 10);

        assertThat(sentTypes()).doesNotContain("BrickGolemSweep");
        assertThat(hp(far)).isEqualTo(1000);
    }

    @Test
    void doesNotMoveAndIgnoresStunAndKnockbackState() {
        PveBrickGolemMob golem = golem(0.1f);
        GameObject enemy = victim(Master.LeftPlayer, new Vector3(7f, 0f, 5f));
        golem.getGameObject().getComponents().add(new RigidBody(golem.getGameObject(), 10));

        golem.setStun(5f);
        tick(golem, 100);

        assertThat(golem.getGameObject().getPosition().getX()).isEqualTo(GOLEM_POSITION.getX());
        assertThat(golem.getGameObject().getPosition().getZ()).isEqualTo(GOLEM_POSITION.getZ());
        assertThat(golem.getSpeed().total()).isZero();
        assertThat(hp(enemy)).isLessThan(1000);
    }

    @Test
    void airUnitsAreNeverTargeted() {
        PveBrickGolemMob golem = golem(0.1f);
        GameObject flyer = victim(Master.LeftPlayer, new Vector3(7f, 3f, 5f));
        assertThat(TargetMask.of(flyer)).isEqualTo(TargetMask.AIR.bit);

        tick(golem, 100);

        assertThat(sentTypes()).isEmpty();
        assertThat(hp(flyer)).isEqualTo(1000);
    }

    @Test
    void isAnObjectiveThatEndsWhenDestroyedAndLeavesNoDeathField() {
        PveBrickGolemMob golem = golem(SUB_ATTACK_INTERVAL);
        assertThat(PveObjectiveTarget.isTerminal(golem.getGameObject())).isFalse();

        golem.applyDamage(new com.wordonline.server.game.domain.AttackInfo(5000,
                com.wordonline.server.game.domain.magic.ElementType.NONE));

        assertThat(golem.getGameObject().isDestroyed()).isTrue();
        assertThat(PveObjectiveTarget.isTerminal(golem.getGameObject())).isTrue();
    }

    private void tick(PveBrickGolemMob golem, int frames) {
        for (int i = 0; i < frames; i++) {
            golem.update();
            sessionData.gameObjects.forEach(object -> object.getComponents().forEach(component -> {
                if (component != golem) {
                    component.update();
                }
            }));
        }
    }

    private void tickUntil(PveBrickGolemMob golem, java.util.function.BooleanSupplier reached) {
        for (int i = 0; i < 400 && !reached.getAsBoolean(); i++) {
            tick(golem, 1);
        }
        assertThat(reached.getAsBoolean()).isTrue();
    }

    private PveBrickGolemMob golem(float subAttackInterval) {
        GameObject object = new GameObject(Master.RightPlayer, PrefabType.PveBrickGolem,
                new Vector3(GOLEM_POSITION), gameContext);
        object.setStatus(Status.Idle);
        object.addCollider(new CircleCollider(object, 2f, true));
        PveBrickGolemMob mob = new PveBrickGolemMob(object, 3000, DAMAGE, ATTACK_INTERVAL, ATTACK_RANGE,
                EFFECT_RADIUS, SUB_DAMAGE, subAttackInterval, SUB_ATTACK_RANGE);
        object.getComponents().add(mob);
        mob.start();
        sessionData.gameObjects.add(object);
        return mob;
    }

    private GameObject victim(Master master, Vector3 position) {
        GameObject victim = new GameObject(master, PrefabType.RockSlime, position, gameContext);
        victim.setStatus(Status.Idle);
        victim.addCollider(new CircleCollider(victim, 0.5f, false));
        victim.getComponents().add(new TargetDummy(victim));
        sessionData.gameObjects.add(victim);
        return victim;
    }

    private RecordingReceiver receiverOn(GameObject victim) {
        RecordingReceiver receiver = new RecordingReceiver(victim);
        victim.getComponents().add(receiver);
        return receiver;
    }

    private int hp(GameObject victim) {
        return victim.getComponent(TargetDummy.class).getHp();
    }

    private List<ProjectileDto> sent() {
        sentProjectiles.addAll(objectsInfoDtoBuilder.getObjectsInfoDto().projectile());
        return sentProjectiles;
    }

    private List<String> sentTypes() {
        return sent().stream().map(ProjectileDto::getType).toList();
    }

    private ProjectileDto projectile(String type) {
        return sent().stream().filter(dto -> type.equals(dto.getType())).findFirst().orElseThrow();
    }

    private static final class RecordingReceiver extends Component implements EffectReceiver {
        private Effect effect;
        private Vector3 direction;
        private float strength;

        private RecordingReceiver(GameObject gameObject) {
            super(gameObject);
        }

        @Override
        public void onReceive(Effect effect) {
        }

        @Override
        public void onReceive(Effect effect, Vector3 direction, float prox) {
            this.effect = effect;
            this.direction = direction;
            this.strength = prox;
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

    private static final class TargetDummy extends BehaviorMob {

        private TargetDummy(GameObject gameObject) {
            super(gameObject, 1000, 0f, TargetMask.ANY.bit, 0f, 0f, null);
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
