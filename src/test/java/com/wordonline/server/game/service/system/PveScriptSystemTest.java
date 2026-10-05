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
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
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
    void installerDestroyedFiresOnceTheObjectIsDestroyed() {
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

        boss.destroy();
        world.remove(boss);
        system.update(context);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(PveScriptEventDto.class));
    }

    // Missing from gameContext.getGameObjects() is not destroyed: a new object joins that list a frame late.
    @Test
    void installerDestroyedDoesNotFireForAnObjectOnlyAbsentFromTheWorldList() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        GameObject boss = installBoss(installer, context, "boss", 100);
        world.remove(boss);

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.InstallerDestroyed, 0, "boss", List.of("dead"), List.of())
        ), PveScenarioRules.defaultRules());
        newSystem(scenario, installer).update(context);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());
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
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
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
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
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
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
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
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
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

    // A broken action is logged and skipped: the event's next action and the match go on.
    @Test
    void aFailingActionDoesNotStopTheNextAction() {
        GameContext context = newGameContext();
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        java.util.concurrent.atomic.AtomicBoolean firstCall = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstCall.getAndSet(false)) {
                throw new IllegalArgumentException("missing parameter row");
            }
            world.add(invocation.getArgument(0));
            return null;
        }).when(context).createGameObject(any());

        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 0, null, List.of(), List.of(
                        new PveSpawnWaveAction(PrefabType.ZapMouse, 1, 14, 3),
                        new PveSpawnWaveAction(PrefabType.ZapMouse, 2, 14, 7)))
        ), PveScenarioRules.defaultRules());
        PveScriptSystem system = newSystem(scenario, installer);

        when(context.getFrameNum()).thenReturn(0);
        org.assertj.core.api.Assertions.assertThatCode(() -> system.update(context)).doesNotThrowAnyException();
        assertThat(world).hasSize(2);
    }

    // ---- seq and replay ----

    private PveScriptSystem systemWithTwoSecondsEvents(GameContext context) {
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), context);
        var scenario = new PveScenario(List.of(), List.of(), List.of(
                event("e1", PveTriggerType.FrameNumGte, 10, null, List.of("one"), List.of()),
                event("silent", PveTriggerType.FrameNumGte, 10, null, List.of(), List.of()),
                event("e2", PveTriggerType.FrameNumGte, 20, null, List.of("two"), List.of()),
                event("e3", PveTriggerType.FrameNumGte, 300, null, List.of("three"), List.of())
        ), PveScenarioRules.defaultRules());
        return newSystem(scenario, installer);
    }

    private List<PveScriptEventDto> sentTo(long userId) {
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(sessionObject, org.mockito.Mockito.atLeast(0)).sendFrameInfo(org.mockito.ArgumentMatchers.eq(userId), captor.capture());
        return captor.getAllValues().stream().map(PveScriptEventDto.class::cast).toList();
    }

    @Test
    void numbersEventsWithLinesFromOneAndSkipsEventsWithoutLines() {
        GameContext context = newGameContext();
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);
        PveScriptSystem system = systemWithTwoSecondsEvents(context);

        for (int frame : new int[] {10, 20, 300}) {
            when(context.getFrameNum()).thenReturn(frame);
            system.update(context);
        }

        List<PveScriptEventDto> sent = sentTo(1L);
        assertThat(sent).extracting(PveScriptEventDto::seq).containsExactly(1, 2, 3);
        assertThat(sent).extracting(PveScriptEventDto::key).containsExactly("key.e1", "key.e2", "key.e3");
        assertThat(sentTo(2L)).extracting(PveScriptEventDto::seq).containsExactly(1, 2, 3);
    }

    @Test
    void replaysOnlyNewerAndRecentEventsInOrderToTheAskingUser() {
        GameContext context = newGameContext();
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);
        PveScriptSystem system = systemWithTwoSecondsEvents(context);
        for (int frame : new int[] {10, 20}) {
            when(context.getFrameNum()).thenReturn(frame);
            system.update(context);
        }
        org.mockito.Mockito.clearInvocations(sessionObject);

        when(context.getFrameNum()).thenReturn(30);
        system.sendRecentEventsTo(context, 7L, 0);

        assertThat(sentTo(7L)).extracting(PveScriptEventDto::seq).containsExactly(1, 2);
        verify(sessionObject, never()).sendFrameInfo(org.mockito.ArgumentMatchers.eq(1L), any());
        verify(sessionObject, never()).sendFrameInfo(org.mockito.ArgumentMatchers.eq(2L), any());

        org.mockito.Mockito.clearInvocations(sessionObject);
        system.sendRecentEventsTo(context, 7L, 1);
        assertThat(sentTo(7L)).extracting(PveScriptEventDto::seq).containsExactly(2);

        org.mockito.Mockito.clearInvocations(sessionObject);
        system.sendRecentEventsTo(context, 7L, 2);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any());
    }

    @Test
    void doesNotReplayEventsOlderThanTheReplayWindow() {
        GameContext context = newGameContext();
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);
        PveScriptSystem system = systemWithTwoSecondsEvents(context);
        for (int frame : new int[] {10, 20}) {
            when(context.getFrameNum()).thenReturn(frame);
            system.update(context);
        }
        org.mockito.Mockito.clearInvocations(sessionObject);
        int window = PveScriptSystem.REPLAY_SECONDS * com.wordonline.server.game.service.GameLoop.FPS;

        // seq 1 was sent at frame 10, seq 2 at frame 20: only seq 2 is still inside the window.
        when(context.getFrameNum()).thenReturn(10 + window + 1);
        system.sendRecentEventsTo(context, 7L, 0);

        assertThat(sentTo(7L)).extracting(PveScriptEventDto::seq).containsExactly(2);

        // The next live event keeps counting after the replayed ones were pruned.
        org.mockito.Mockito.clearInvocations(sessionObject);
        when(context.getFrameNum()).thenReturn(300);
        system.update(context);
        assertThat(sentTo(1L)).extracting(PveScriptEventDto::seq).containsExactly(3);
    }
}
