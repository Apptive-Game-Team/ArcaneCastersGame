package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.EvilEntMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveEvilEntMob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.prefab.implement.misc.third.EvilEntPrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PveEvilEntPrefabInitializerTest {

    private static final float TICK_SEC = 0.05f;
    private static final float PLAYER_RADIUS = 1f;
    // The arena is x 0..18; the boss stands at x = 14 and the player at x = 1.
    private static final Vector3 BOSS_POSITION = new Vector3(14, 0, 5);
    private static final Vector3 PLAYER_POSITION = new Vector3(1, 0, 5);

    private Parameters parameters;
    private GameContext gameContext;
    private GameSessionData sessionData;
    private GameObjectParameters pveEnt;

    @BeforeEach
    void setUp() {
        parameters = mock(Parameters.class);
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(TICK_SEC);
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(new ObjectsInfoDtoBuilder(gameContext));

        pveEnt = entParameters(3000, 20f, 20f);
        when(parameters.object(GameObjectKey.PVE_EVIL_ENT)).thenReturn(pveEnt);
    }

    private static GameObjectParameters entParameters(int hp, float attackRange, float subAttackRange) {
        GameObjectParameters p = mock(GameObjectParameters.class);
        when(p.intValue(ParameterKey.HP)).thenReturn(hp);
        when(p.intValue(ParameterKey.MASS)).thenReturn(10);
        when(p.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(p.intValue(ParameterKey.DAMAGE)).thenReturn(9);
        when(p.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1.8f);
        when(p.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(attackRange);
        when(p.floatValue(ParameterKey.PROJECTILE_SPEED)).thenReturn(14f);
        when(p.intValue(ParameterKey.SUB_DAMAGE)).thenReturn(28);
        when(p.floatValue(ParameterKey.SUB_ATTACK_RANGE)).thenReturn(subAttackRange);
        when(p.floatValue(ParameterKey.SUB_ATTACK_INTERVAL)).thenReturn(12f);
        when(p.floatValue(ParameterKey.PULL_MASS_LIMIT)).thenReturn(5f);
        return p;
    }

    private GameObject newBoss() {
        GameObject boss = new GameObject(Master.RightPlayer, PrefabType.PveEvilEnt, new Vector3(BOSS_POSITION), gameContext);
        boss.setStatus(Status.Idle);
        new PveEvilEntPrefabInitializer(parameters).initialize(boss);
        boss.flushComponents();
        sessionData.gameObjects.add(boss);
        return boss;
    }

    private GameObject newTarget(PrefabType type, Vector3 position, boolean withBody) {
        GameObject target = new GameObject(Master.LeftPlayer, type, new Vector3(position), gameContext);
        target.setStatus(Status.Idle);
        target.addCollider(new CircleCollider(target, PLAYER_RADIUS, false));
        if (withBody) {
            target.getComponents().add(new RigidBody(target, 1));
        }
        target.getComponents().add(new TargetDummy(target));
        target.flushComponents();
        sessionData.gameObjects.add(target);
        return target;
    }

    // Ticks the boss and every other object in the session, like the game loop does.
    private void tick(GameObject boss, int frames) {
        for (int i = 0; i < frames; i++) {
            for (GameObject object : new ArrayList<>(sessionData.gameObjects)) {
                for (Component component : new ArrayList<>(object.getComponents())) {
                    component.update();
                }
            }
        }
    }

    @Test
    void registersUnderItsOwnNames() {
        assertThat(PrefabType.PveEvilEnt.getBeanName()).isEqualTo("pve_evil_ent_prefab");
        assertThat(GameObjectKey.PVE_EVIL_ENT.dbName()).isEqualTo("pve_evil_ent");
    }

    @Test
    void isStationaryAndIsTheHealthMobTheInstallerFinds() {
        GameObject boss = newBoss();
        newTarget(PrefabType.Player, PLAYER_POSITION, false);

        tick(boss, 20 * 30);

        assertThat(boss.getPosition().getX()).isEqualTo(BOSS_POSITION.getX());
        assertThat(boss.getPosition().getZ()).isEqualTo(BOSS_POSITION.getZ());
        Mob mob = PveScenarioInstaller.findHealthMob(boss);
        assertThat(mob).isInstanceOf(PveEvilEntMob.class);
        assertThat(mob.getSpeed().total()).isZero();
        assertThat(mob.getMaxHp()).isEqualTo(3000);
        assertThat(boss.getComponents(Collidable.class)).isNotEmpty();
        assertThat(boss.getElement().nativeHas(ElementType.NATURE)).isTrue();
        assertThat(boss.getElement().nativeHas(ElementType.FIRE)).isTrue();
    }

    @Test
    void maxHpOverrideApplies() {
        Mob mob = PveScenarioInstaller.findHealthMob(newBoss());

        mob.overrideMaxHp(400);

        assertThat(mob.getMaxHp()).isEqualTo(400);
        assertThat(mob.getHp()).isEqualTo(400);
    }

    @Test
    void destroyedBossIsTerminalAndLeavesNoDeathField() {
        GameObject boss = newBoss();
        assertThat(PveObjectiveTarget.isTerminal(boss)).isFalse();

        boss.getComponent(Mob.class).applyDamage(new AttackInfo(5000, ElementType.NONE));

        assertThat(boss.isDestroyed()).isTrue();
        assertThat(PveObjectiveTarget.isTerminal(boss)).isTrue();
        var created = org.mockito.ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, atLeast(0)).createGameObject(created.capture());
        assertThat(created.getAllValues()).extracting(GameObject::getType)
                .noneMatch(t -> t == PrefabType.FireField || t == PrefabType.LeafField);
    }

    @Test
    void hpPercentTriggerReadsTheBossHp() {
        GameObject boss = newBoss();
        Mob mob = PveScenarioInstaller.findHealthMob(boss);

        mob.applyDamage(new AttackInfo(1500, ElementType.NONE));

        assertThat(mob.getHp()).isEqualTo(1500);
        assertThat((float) mob.getHp() / mob.getMaxHp()).isEqualTo(0.5f);
    }

    @Test
    void punchesThePlayerFromX14WhenTheRangeCoversTheArena() {
        GameObject boss = newBoss();
        GameObject player = newTarget(PrefabType.Player, PLAYER_POSITION, false);
        Mob playerMob = player.getComponent(Mob.class);

        tick(boss, 20 * 10);

        assertThat(playerMob.getHp()).isLessThan(playerMob.getMaxHp());
        assertThat(boss.getPosition().getX()).isEqualTo(BOSS_POSITION.getX());
    }

    @Test
    void theCardUnitRangesDoNotReachAcrossTheArena() {
        // evil_ent attack_range 5 and sub_attack_range 6: 13 apart minus the 1.0 player radius
        // is 12 of open ground, so a stationary boss with those values never fires.
        GameObjectParameters cardRanges = entParameters(3000, 5f, 6f);
        when(parameters.object(GameObjectKey.PVE_EVIL_ENT)).thenReturn(cardRanges);
        GameObject boss = newBoss();
        GameObject player = newTarget(PrefabType.Player, PLAYER_POSITION, false);
        Mob playerMob = player.getComponent(Mob.class);

        tick(boss, 20 * 30);

        assertThat(playerMob.getHp()).isEqualTo(playerMob.getMaxHp());
    }

    @Test
    void dragsAPlayerUnitAcrossTheArenaAndHitsItWithTheFireFist() {
        GameObject boss = newBoss();
        GameObject unit = newTarget(PrefabType.RockSlime, new Vector3(2, 0, 5), true);
        Mob unitMob = unit.getComponent(Mob.class);

        tick(boss, 20 * 16); // the first grab charge arrives after sub_attack_interval (12 s)

        assertThat(unit.getPosition().getX()).isGreaterThan(10f);
        assertThat(unitMob.getHp()).isLessThan(unitMob.getMaxHp() - 9);
    }

    @Test
    void switchesItsPunchesToAUnitThatAppearsCloserThanThePlayer() {
        GameObject boss = newBoss();
        GameObject player = newTarget(PrefabType.Player, PLAYER_POSITION, false);
        Mob playerMob = player.getComponent(Mob.class);

        tick(boss, 20 * 4); // alone on the field, the boss locks on the player
        int playerHpWhenTheUnitAppears = playerMob.getHp();
        assertThat(playerHpWhenTheUnitAppears).isLessThan(playerMob.getMaxHp());

        GameObject unit = newTarget(PrefabType.RockSlime, new Vector3(10, 0, 5), false);
        Mob unitMob = unit.getComponent(Mob.class);
        tick(boss, 20 * 12);

        assertThat(unitMob.getHp()).isLessThan(unitMob.getMaxHp());
        // One more punch can land before the next once-a-second look for a closer enemy, no more.
        assertThat(playerMob.getHp()).isGreaterThanOrEqualTo(playerHpWhenTheUnitAppears - 9);
    }

    @Test
    void returnsToThePlayerWhenTheUnitsAreGone() {
        GameObject boss = newBoss();
        GameObject player = newTarget(PrefabType.Player, PLAYER_POSITION, false);
        Mob playerMob = player.getComponent(Mob.class);
        GameObject unit = newTarget(PrefabType.RockSlime, new Vector3(10, 0, 5), false);

        tick(boss, 20 * 3);
        assertThat(playerMob.getHp()).isEqualTo(playerMob.getMaxHp());

        unit.setStatus(Status.Destroyed);
        sessionData.gameObjects.remove(unit);
        tick(boss, 20 * 6);

        assertThat(playerMob.getHp()).isLessThan(playerMob.getMaxHp());
    }

    @Test
    void cardEvilEntIsUnchanged() {
        GameObjectParameters card = entParameters(180, 5f, 6f);
        when(card.floatValue(ParameterKey.SPEED)).thenReturn(0.45f);
        when(parameters.object(GameObjectKey.EVIL_ENT)).thenReturn(card);
        GameObject ent = new GameObject(Master.RightPlayer, PrefabType.EvilEnt, new Vector3(BOSS_POSITION), gameContext);
        new EvilEntPrefabInitializer(parameters).initialize(ent);
        ent.flushComponents();

        Mob mob = ent.getComponent(Mob.class);
        assertThat(mob).isInstanceOf(EvilEntMob.class).isNotInstanceOf(PveEvilEntMob.class);
        assertThat(mob.getSpeed().total()).isEqualTo(0.45f);
        assertThat(ent.getComponent(PveObjectiveTarget.class)).isNull();
        ent.getComponent(Mob.class).applyDamage(new AttackInfo(5000, ElementType.NONE));
        var created = org.mockito.ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, atLeast(0)).createGameObject(created.capture());
        assertThat(created.getAllValues()).extracting(GameObject::getType)
                .anyMatch(t -> t == PrefabType.FireField || t == PrefabType.LeafField);
    }

    /** Just enough of a mob for the boss's detector to see the target and for damage to land. */
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
    }
}
