package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveGateKeeperMob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.prefab.implement.misc.DimensionToadPrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PveDimensionToadPrefabInitializerTest {

    private static final float TICK_SEC = 0.05f;
    private static final float SPAWN_INTERVAL_SEC = 10f;

    private Parameters parameters;
    private GameContext gameContext;

    @BeforeEach
    void setUp() {
        parameters = mock(Parameters.class);
        gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(TICK_SEC);

        GameObjectParameters toad = mock(GameObjectParameters.class);
        when(toad.intValue(ParameterKey.HP)).thenReturn(800);
        when(toad.intValue(ParameterKey.MASS)).thenReturn(1000);
        when(toad.floatValue(ParameterKey.RADIUS)).thenReturn(0.65f);
        when(toad.floatValue(ParameterKey.SPAWN_INTERVAL)).thenReturn(SPAWN_INTERVAL_SEC);
        when(parameters.object(GameObjectKey.PVE_DIMENSION_TOAD)).thenReturn(toad);

        GameObjectParameters fire = tadpole(30, 12, 0.9f, 1.0f, 20f);
        GameObjectParameters lightning = tadpole(30, 10, 1.0f, 1.0f, 20f);
        when(parameters.object(GameObjectKey.PVE_FIRE_TADPOLE)).thenReturn(fire);
        when(parameters.object(GameObjectKey.PVE_LIGHTNING_TADPOLE)).thenReturn(lightning);
    }

    private static GameObjectParameters tadpole(int hp, int damage, float speed, float attackInterval, float duration) {
        GameObjectParameters p = mock(GameObjectParameters.class);
        when(p.intValue(ParameterKey.HP)).thenReturn(hp);
        when(p.intValue(ParameterKey.DAMAGE)).thenReturn(damage);
        when(p.intValue(ParameterKey.MASS)).thenReturn(1);
        when(p.floatValue(ParameterKey.SPEED)).thenReturn(speed);
        when(p.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(attackInterval);
        when(p.floatValue(ParameterKey.RADIUS)).thenReturn(0.3f);
        when(p.floatValue(ParameterKey.DURATION)).thenReturn(duration);
        return p;
    }

    private GameObject newToad() {
        GameObject toad = new GameObject(Master.RightPlayer, PrefabType.PveDimensionToad, new Vector3(14, 0, 5), gameContext);
        new PveDimensionToadPrefabInitializer(parameters).initialize(toad);
        toad.flushComponents();
        return toad;
    }

    private void tick(GameObject gameObject, int frames) {
        for (int i = 0; i < frames; i++) {
            for (Component component : new ArrayList<>(gameObject.getComponents())) {
                component.update();
            }
        }
    }

    private List<GameObject> created() {
        ArgumentCaptor<GameObject> captor = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, atLeast(0)).createGameObject(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void registersUnderItsOwnPrefabTypes() {
        assertThat(PrefabType.PveDimensionToad.getBeanName()).isEqualTo("pve_dimension_toad_prefab");
        assertThat(PrefabType.PveFireTadpole.getBeanName()).isEqualTo("pve_fire_tadpole_prefab");
        assertThat(PrefabType.PveLightningTadpole.getBeanName()).isEqualTo("pve_lightning_tadpole_prefab");
    }

    @Test
    void staysPutAndNeverAttacksEvenWithAThreatNextToIt() {
        GameObject toad = newToad();
        new GameObject(Master.LeftPlayer, PrefabType.LeafSlime, new Vector3(13, 0, 5), gameContext);
        Vector3 before = new Vector3(toad.getPosition());

        tick(toad, 20 * 30);

        assertThat(toad.getPosition().getX()).isEqualTo(before.getX());
        assertThat(toad.getPosition().getZ()).isEqualTo(before.getZ());
        Mob mob = toad.getComponent(Mob.class);
        assertThat(mob).isInstanceOf(PveGateKeeperMob.class);
        assertThat(mob.getSpeed().total()).isZero();
        assertThat(toad.getComponents(MeleeAttackMob.class)).isEmpty();
        assertThat(toad.getComponents(Collidable.class)).isNotEmpty();
        assertThat(toad.getElement().nativeHas(ElementType.FIRE)).isTrue();
        assertThat(toad.getElement().nativeHas(ElementType.LIGHTNING)).isTrue();
    }

    @Test
    void spawnsFireAndLightningTadpolesInTurnAtTheParameterInterval() {
        GameObject toad = newToad();

        // spawn on summon: the first tick spawns, then one full interval per spawn
        tick(toad, 1);
        assertThat(spawnedTadpoles()).containsExactly(PrefabType.PveFireTadpole);
        tick(toad, (int) (SPAWN_INTERVAL_SEC / TICK_SEC) - 2);
        assertThat(spawnedTadpoles()).hasSize(1);
        tick(toad, 2);
        assertThat(spawnedTadpoles()).containsExactly(PrefabType.PveFireTadpole, PrefabType.PveLightningTadpole);
        tick(toad, (int) (SPAWN_INTERVAL_SEC / TICK_SEC));
        assertThat(spawnedTadpoles())
                .containsExactly(PrefabType.PveFireTadpole, PrefabType.PveLightningTadpole, PrefabType.PveFireTadpole);
    }

    private List<PrefabType> spawnedTadpoles() {
        return created().stream().map(GameObject::getType)
                .filter(t -> t == PrefabType.PveFireTadpole || t == PrefabType.PveLightningTadpole)
                .toList();
    }

    @Test
    void maxHpOverrideFromTheInstallerReachesTheGateKeeperMob() {
        GameObject toad = newToad();

        Mob mob = PveScenarioInstaller.findHealthMob(toad);
        assertThat(mob).isInstanceOf(PveGateKeeperMob.class);
        assertThat(mob.getMaxHp()).isEqualTo(800);

        mob.overrideMaxHp(200);

        assertThat(mob.getMaxHp()).isEqualTo(200);
        assertThat(mob.getHp()).isEqualTo(200);
    }

    @Test
    void killedToadIsTerminalAndLeavesNoDeathField() {
        GameObject toad = newToad();
        Mob mob = toad.getComponent(Mob.class);
        assertThat(PveObjectiveTarget.isTerminal(toad)).isFalse();

        mob.applyDamage(new AttackInfo(5000, ElementType.NONE));

        assertThat(toad.isDestroyed()).isTrue();
        assertThat(PveObjectiveTarget.isTerminal(toad)).isTrue();
        assertThat(created()).extracting(GameObject::getType)
                .noneMatch(t -> t == PrefabType.FireField || t == PrefabType.ElectricField
                        || t == PrefabType.WaterField || t == PrefabType.LeafField);
    }

    @Test
    void theCardToadStillLeavesAFieldWhenItDies() {
        GameObjectParameters card = mock(GameObjectParameters.class);
        when(card.intValue(ParameterKey.HP)).thenReturn(320);
        when(card.intValue(ParameterKey.MASS)).thenReturn(2);
        when(card.floatValue(ParameterKey.RADIUS)).thenReturn(0.65f);
        when(parameters.object(GameObjectKey.DIMENSION_TOAD)).thenReturn(card);
        GameObject toad = new GameObject(Master.RightPlayer, PrefabType.DimensionToad, Vector3.ZERO, gameContext);
        new DimensionToadPrefabInitializer(parameters).initialize(toad);
        toad.flushComponents();

        toad.getComponent(Mob.class).applyDamage(new AttackInfo(5000, ElementType.NONE));

        assertThat(created()).extracting(GameObject::getType)
                .anyMatch(t -> t == PrefabType.FireField || t == PrefabType.ElectricField);
    }

    @Test
    void tadpolesExpireAfterTheirOwnDuration() {
        for (var entry : List.of(
                new Object[]{PrefabType.PveFireTadpole, new PveFireTadpolePrefabInitializer(parameters)},
                new Object[]{PrefabType.PveLightningTadpole, new PveLightningTadpolePrefabInitializer(parameters)})) {
            GameObject tadpole = new GameObject(Master.RightPlayer, (PrefabType) entry[0], Vector3.ZERO, gameContext);
            ((com.wordonline.server.game.domain.object.prefab.PrefabInitializer) entry[1]).initialize(tadpole);
            tadpole.flushComponents();

            assertThat(tadpole.getComponent(Mob.class)).isInstanceOf(MeleeAttackMob.class);
            TimedSelfDestroyer lifetime = tadpole.getComponent(TimedSelfDestroyer.class);
            lifetime.update();
            assertThat(tadpole.isDestroyed()).isFalse();
            for (int i = 0; i < 20 * 20; i++) {
                lifetime.update();
            }
            assertThat(tadpole.isDestroyed()).as(entry[0].toString()).isTrue();
        }
    }
}
