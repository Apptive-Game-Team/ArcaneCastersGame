package com.wordonline.server.playground;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.*;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlaygroundLoopTest {
    private final Clock clock = mock(Clock.class);
    private final GameContext context = mock(GameContext.class);
    private final SessionObject session = mock(SessionObject.class);
    private final GameSessionData data = new GameSessionData(
            new PlayerData(mock(ManaCharger.class)), new PlayerData(mock(ManaCharger.class)));
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-06T00:00:00Z"));
    private final AtomicReference<Consumer<GameObject>> initializer = new AtomicReference<>();
    private final AtomicReference<ResultChecker> results = new AtomicReference<>();
    private final List<Runnable> commands = new ArrayList<>();
    private PlaygroundLoop loop;

    @BeforeEach void setup() {
        when(clock.instant()).thenAnswer(call -> now.get());
        when(context.getGameSessionData()).thenReturn(data);
        when(context.getGameTimer()).thenReturn(mock(GameTimer.class));
        when(session.getLeftUserId()).thenReturn(123L);
        when(session.getPingChecker()).thenReturn(mock(PingChecker.class));
        doAnswer(call -> { initializer.set(call.getArgument(0)); return null; }).when(context).setObjectInitializer(any());
        doAnswer(call -> { results.set(call.getArgument(0)); return null; }).when(context).setResultChecker(any());
        when(context.submitAction(anyString(), any())).thenAnswer(call -> { commands.add(call.getArgument(1)); return true; });
        loop = spy(new PlaygroundLoop(mock(MmrService.class), mock(UserService.class), context,
                mock(Parameters.class), mock(SyncFrameDataSystem.class), mock(GameActionSystem.class),
                mock(BotAgentSystem.class), mock(FeverTimeSystem.class), mock(GameObjectStateInitialSystem.class),
                mock(ComponentUpdateSystem.class), mock(PhysicSystem.class), mock(GameObjectAddRemoteSystem.class),
                mock(DatabaseMagicParser.class), mock(BotPersonaService.class), mock(BotCounterEvaluator.class), clock));
        loop.init(session, () -> {});
        doReturn(true).when(loop).is_running();
    }

    @Test void expiresAtExactly300SecondsWithoutSubscribersOrFrames() {
        assertThat(loop.getExpiresAt()).isEqualTo(now.get().plusSeconds(300));
        now.set(now.get().plusSeconds(299));
        assertThat(loop.acceptsCommands()).isTrue();
        loop.beforeResultCheck();
        verify(loop, never()).close();
        now.set(now.get().plusSeconds(1));
        loop.beforeResultCheck();
        verify(loop).close();
        verify(session).sendFrameInfo(eq(123L), any());
        assertThat(loop.acceptsCommands()).isFalse();
    }

    @Test void commandsRunOnTheLoopAndExpiredQueuedCommandsDoNothing() {
        var applied = new java.util.concurrent.atomic.AtomicBoolean();
        var reply = loop.command("cast", () -> { applied.set(true); return loop.reply(true, "cast"); });
        assertThat(applied.get()).isFalse();
        now.set(now.get().plusSeconds(300));
        commands.removeFirst().run();
        assertThat(reply.join().success()).isFalse();
        assertThat(applied.get()).isFalse();
    }

    @Test void closesEvenWhenEndNotificationFails() {
        doThrow(new IllegalStateException("broker unavailable")).when(session).sendFrameInfo(eq(123L), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> loop.end("CLOSED"))
                .isInstanceOf(IllegalStateException.class);
        verify(loop).close();
        assertThat(loop.acceptsCommands()).isFalse();
    }

    @Test void canceledHttpCommandDoesNotRunLater() {
        var applied = new java.util.concurrent.atomic.AtomicBoolean();
        var reply = loop.command("cast", () -> { applied.set(true); return loop.reply(true, "cast"); });
        reply.cancel(false);
        commands.removeFirst().run();
        assertThat(applied.get()).isFalse();
    }

    @Test void playerMaximumAndCurrentHpAreOverriddenWithoutEnablingResults() {
        var player = new GameObject(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        var mob = new TestMob(player);
        player.addComponent(mob);
        player.flushComponents();
        initializer.get().accept(player);
        assertThat(mob.getHp()).isEqualTo(99_999_999);
        assertThat(mob.getMaxHp()).isEqualTo(99_999_999);
        assertThat(data.leftPlayerData.hp).isEqualTo(99_999_999);
        results.get().setLoser(Master.LeftPlayer);
        assertThat(results.get().checkResult()).isFalse();
        assertThat(results.get().getLoser()).isNull();
    }

    @Test void factionImmunityCoversExistingFutureAndConvertedUnitsAndAllowsHealing() {
        var first = unit(Master.LeftPlayer, PrefabType.ZapMouse);
        loop.setImmune(Master.LeftPlayer, true);
        var future = unit(Master.LeftPlayer, PrefabType.ZapMouse);
        first.getComponent(Mob.class).applyDamage(new AttackInfo(25, ElementType.NONE));
        future.getComponent(Mob.class).onDamaged(new AttackInfo(25, ElementType.NONE));
        assertThat(first.getComponent(Mob.class).getHp()).isEqualTo(100);
        assertThat(future.getComponent(Mob.class).getHp()).isEqualTo(100);
        future.setMaster(Master.RightPlayer);
        future.getComponent(Mob.class).applyDamage(new AttackInfo(25, ElementType.NONE));
        assertThat(future.getComponent(Mob.class).getHp()).isEqualTo(75);
        future.getComponent(Mob.class).applyDamage(new AttackInfo(-10, ElementType.NONE));
        assertThat(future.getComponent(Mob.class).getHp()).isEqualTo(85);
        loop.setImmune(Master.LeftPlayer, false);
        first.getComponent(Mob.class).applyDamage(new AttackInfo(25, ElementType.NONE));
        assertThat(first.getComponent(Mob.class).getHp()).isEqualTo(75);
    }

    @Test void clearPurgesActiveAndPendingFactionObjectsPreservingPlayersWallsAndEnemyCreates() {
        var ally = unit(Master.LeftPlayer, PrefabType.ZapMouse);
        var enemy = unit(Master.RightPlayer, PrefabType.ZapMouse);
        var player = new GameObject(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        var wall = new GameObject(Master.None, PrefabType.Wall, Vector3.ZERO, context);
        var pendingAlly = unit(Master.LeftPlayer, PrefabType.ZapMouse);
        var pendingEnemy = unit(Master.RightPlayer, PrefabType.ZapMouse);
        data.gameObjects.addAll(List.of(ally, enemy, player, wall));
        data.gameObjectsToAdd.addAll(List.of(pendingAlly, pendingEnemy));
        loop.clear(Master.LeftPlayer);
        assertThat(data.gameObjects).containsExactly(enemy, player, wall);
        assertThat(data.gameObjectsToAdd).containsExactly(pendingEnemy);
        assertThat(ally.isDestroyed()).isTrue();
        assertThat(pendingAlly.isDestroyed()).isTrue();
        loop.clear(Master.None);
        assertThat(data.gameObjects).containsExactly(player, wall);
        assertThat(data.gameObjectsToAdd).isEmpty();
    }

    @Test void clearDoesNotExecuteDestructionOrCombatDeathCallbacks() {
        var object = unit(Master.LeftPlayer, PrefabType.ZapMouse);
        var listener = mock(com.wordonline.server.game.domain.object.component.Component.class);
        object.addComponent(listener);
        object.flushComponents();
        data.gameObjects.add(object);
        loop.clear(Master.None);
        verify(listener, never()).onDestroy();
        assertThat(object.getComponents()).isEmpty();
    }

    private GameObject unit(Master side, PrefabType type) {
        var object = new GameObject(side, type, Vector3.ZERO, context);
        object.addComponent(new TestMob(object));
        object.flushComponents();
        initializer.get().accept(object);
        return object;
    }
    private static class TestMob extends Mob {
        TestMob(GameObject object) { super(object, 100, 0); }
        @Override public void start() {}
        @Override public void onDeath() {}
        @Override public void onDestroy() {}
    }
}
