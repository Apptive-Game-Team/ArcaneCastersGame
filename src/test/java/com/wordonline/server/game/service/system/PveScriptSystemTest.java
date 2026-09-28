package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.domain.pve.PveInstallObjectAction;
import com.wordonline.server.game.domain.pve.PveScenario;
import com.wordonline.server.game.domain.pve.PveScenarioAction;
import com.wordonline.server.game.domain.pve.PveScenarioEvent;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveSetSpawnerAction;
import com.wordonline.server.game.domain.pve.PveSpawnWaveAction;
import com.wordonline.server.game.domain.pve.PveTriggerType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.pve.PveScriptEventDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PveScriptSystemTest {

    private final List<GameObject> world = new ArrayList<>();
    private final SessionObject sessionObject = mock(SessionObject.class);

    private GameContext newGameContext() {
        GameContext context = mock(GameContext.class);
        when(context.getSessionObject()).thenReturn(sessionObject);
        when(context.getGameObjects()).thenReturn(world);
        doAnswer(invocation -> {
            world.add(invocation.getArgument(0));
            return null;
        }).when(context).createGameObject(any());
        return context;
    }

    private PveScriptSystem newSystem(PveScenario scenario, PveScenarioInstaller installer) {
        PveScriptSystem system = new PveScriptSystem();
        system.setScenario(scenario);
        system.setInstaller(installer);
        system.setRuntime(installer.getRuntime());
        return system;
    }

    private static PveScenarioEvent event(String id, PveTriggerType type, int value, String targetInstallerId,
                                           List<String> lines, List<PveScenarioAction> actions) {
        return new PveScenarioEvent(id, type, value, targetInstallerId, null, "key." + id, lines, actions);
    }

    private static class TestMob extends Mob {
        private TestMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp, 0);
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

    private GameObject installBoss(PveScenarioInstaller installer, GameContext context, String installerId, int maxHp) {
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, maxHp));
            gameObject.flushComponents();
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());

        PveInstallObject spec = new PveInstallObject(installerId, PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null);
        return installer.installOne(spec, context);
    }

    // ---- trigger types ----

    @Test
    void frameNumGteFiresOnceTheFrameIsReached() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 100, null, List.of("hi"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(50);
        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        when(context.getFrameNum()).thenReturn(100);
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    @Test
    void secondsGteFiresOnceElapsedFramesCoverTheSeconds() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.SecondsGte, 5, null, List.of("hi"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(5 * 20 - 1);
        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        when(context.getFrameNum()).thenReturn(5 * 20);
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    @Test
    void installerHpPercentLteFiresOnlyOnceHpDropsToThreshold() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        GameObject boss = installBoss(installer, context, "boss", 100);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerHpPercentLte, 20, "boss", List.of("low hp"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        boss.getComponent(Mob.class).applyDamage(new AttackInfo(50, ElementType.NONE));
        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        boss.getComponent(Mob.class).applyDamage(new AttackInfo(30, ElementType.NONE));
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    @Test
    void installerHpPercentLteDoesNotFireBeforeTheInstallerExists() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerHpPercentLte, 100, "boss", List.of("low hp"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());
    }

    @Test
    void installerDestroyedFiresWhenTheObjectIsRemovedFromTheWorld() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        GameObject boss = installBoss(installer, context, "boss", 100);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerDestroyed, 0, "boss", List.of("dead"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        world.remove(boss);
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    @Test
    void installerDestroyedDoesNotFireBeforeTheInstallerExists() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerDestroyed, 0, "boss", List.of("dead"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());
    }

    // ---- dialogue ----

    @Test
    void doesNotSendDialogueWhenTheEventHasNoLines() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);

        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());
    }

    @Test
    void everyEventFiresAtMostOnce() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of("hi"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);
        system.update(context);
        system.update(context);

        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    // ---- actions ----

    @Test
    void spawnWaveSpawnsCountObjectsSpreadOnXBelongingToRightPlayer() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var action = new PveSpawnWaveAction(PrefabType.ZapMouse, 3, 14, 5);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of(action))
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);

        assertThat(world).hasSize(3);
        assertThat(world).allMatch(go -> go.getMaster() == Master.RightPlayer);
        assertThat(world).allMatch(go -> go.getType() == PrefabType.ZapMouse);
        assertThat(world).extracting(go -> go.getPosition().getX())
                .containsExactlyInAnyOrder(13.5f, 14f, 14.5f);
    }

    @Test
    void installObjectActionRegistersUnderItsInstallerIdAndOverridesHp() {
        GameContext context = newGameContext();
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, 10));
            gameObject.flushComponents();
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());

        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        var action = new PveInstallObjectAction("adds", PrefabType.ZapMouse, 14, 7, 250);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of(action))
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);

        GameObject installed = installer.getInstalledObject(context, "adds");
        assertThat(installed).isNotNull();
        assertThat(installed.getComponent(Mob.class).getMaxHp()).isEqualTo(250);
    }

    @Test
    void setSpawnerReplacesTheExistingSpawner() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, 10));
            gameObject.addComponent(new Spawner(gameObject, 0, PrefabType.ZapMouse));
            gameObject.flushComponents();
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());

        PveInstallObject spec = new PveInstallObject("boss", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null);
        GameObject boss = installer.installOne(spec, context);
        Spawner original = boss.getComponent(Spawner.class);
        assertThat(original).isNotNull();

        var action = new PveSetSpawnerAction("boss", 5, PrefabType.ZapMouse, 3f);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of(action))
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);
        boss.flushComponents();

        Spawner replaced = boss.getComponent(Spawner.class);
        assertThat(replaced).isNotNull().isNotSameAs(original);
    }

    @Test
    void setSpawnerWithZeroCountRemovesTheSpawnerWithoutReplacing() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);

        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, 10));
            gameObject.addComponent(new Spawner(gameObject, 0, PrefabType.ZapMouse));
            gameObject.flushComponents();
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());

        PveInstallObject spec = new PveInstallObject("boss", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null);
        GameObject boss = installer.installOne(spec, context);
        assertThat(boss.getComponent(Spawner.class)).isNotNull();

        var action = new PveSetSpawnerAction("boss", 0, null, null);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of(action))
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        system.update(context);
        boss.flushComponents();

        assertThat(boss.getComponent(Spawner.class)).isNull();
    }

    private static class TestBossMob extends TestMob implements com.wordonline.server.game.domain.pve.PveObjectiveTarget {
        private TestBossMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp);
        }

        @Override
        public boolean isTerminal() {
            return getHp() <= 0;
        }
    }

    // A real PVE boss gets its Spawner (a Mob with hp 0) before its boss mob, so the hp trigger
    // and the max_hp override must look past the first Mob component.
    @Test
    void hpOverrideAndHpTriggerUseTheBossMobNotTheSpawnerAddedBeforeIt() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new Spawner(gameObject, 0, PrefabType.LeafSlime, 10f, false, 1));
            gameObject.addComponent(new TestBossMob(gameObject, 1000));
            gameObject.flushComponents();
            world.add(gameObject);
            return null;
        }).when(context).createGameObject(any());
        GameObject boss = installer.installOne(
                new PveInstallObject("boss", PrefabType.PveNatureSlimeNest, Master.RightPlayer, Vector3.ZERO, 200),
                context);

        TestBossMob bossMob = boss.getComponent(TestBossMob.class);
        assertThat(bossMob.getMaxHp()).isEqualTo(200);
        assertThat(boss.getComponent(Spawner.class).getMaxHp()).isZero();

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerHpPercentLte, 50, "boss", List.of("half"), List.of())
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        system.update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());

        bossMob.applyDamage(new AttackInfo(100, ElementType.NONE));
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }
}
