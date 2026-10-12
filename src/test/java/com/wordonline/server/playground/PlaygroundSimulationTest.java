package com.wordonline.server.playground;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.*;
import com.wordonline.server.game.domain.magic.implement.shoot.FireShotMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.*;
import com.wordonline.server.game.domain.object.prefab.implement.fire.FireShotPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.*;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.*;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlaygroundSimulationTest {
    @Test void realPipelineCreatesBothPlayersAndCastsAndClearsBothSides() throws Exception {
        var values = mock(ParameterService.class);
        when(values.getValue("game", "duration")).thenReturn(180000d);
        when(values.getValue("game", "fever_duration")).thenReturn(30000d);
        when(values.getValue("player", "hp")).thenReturn(1000d);
        when(values.getValue("fire_shot", "speed")).thenReturn(8d);
        when(values.getValue("fire_shot", "radius")).thenReturn(.5d);
        when(values.getValue("fire_shot", "damage")).thenReturn(100d);
        var parameters = new Parameters(values);
        var data = new GameSessionData(new PlayerData(mock(ManaCharger.class)), new PlayerData(mock(ManaCharger.class)));
        var parser = mock(DatabaseMagicParser.class);
        var context = new GameContext(new GameTimer(parameters), data, parameters, mock(MagicInputHandler.class), parser);
        var mmr = mock(MmrService.class);
        var users = mock(UserService.class);
        var loop = spy(new PlaygroundLoop(mmr, users, context, parameters,
                new SyncFrameDataSystem(), new GameActionSystem(), mock(BotAgentSystem.class), new FeverTimeSystem(),
                new GameObjectStateInitialSystem(), new ComponentUpdateSystem(), new PhysicSystem(),
                new GameObjectAddRemoteSystem(), parser, mock(BotPersonaService.class), mock(BotCounterEvaluator.class), Clock.systemUTC()));
        doReturn(true).when(loop).is_running();
        var session = new SessionObject("playground-fixture", 123L, -1L, mock(SimpMessagingTemplate.class),
                List.of(), List.of(), SessionType.Playground);
        session.setGameLoop(loop);
        try (var prefabs = mockStatic(PrefabProvider.class)) {
            prefabs.when(() -> PrefabProvider.get(PrefabType.Player)).thenReturn(new PlayerPrefabInitializer(parameters));
            prefabs.when(() -> PrefabProvider.get(PrefabType.Wall)).thenReturn(new WallPrefabInitializer(parameters));
            prefabs.when(() -> PrefabProvider.get(PrefabType.FireShot)).thenReturn(new FireShotPrefabInitializer(parameters));
            loop.init(session, () -> {});
            tick(loop, context);
            for (var side : List.of(Master.LeftPlayer, Master.RightPlayer)) {
                var player = context.findPlayerGameObject(side).orElseThrow();
                assertThat(player.getComponent(Mob.class).getHp()).isEqualTo(99_999_999);
                assertThat(player.getComponent(Mob.class).getMaxHp()).isEqualTo(99_999_999);
                assertThat(player.getPosition()).isEqualTo(side == Master.LeftPlayer
                        ? GameConfig.LEFT_PLAYER_POSITION : GameConfig.RIGHT_PLAYER_POSITION);
                var result = loop.command("cast", () -> {
                    new FireShotMagic().run(context, side, new Vector3(9, 0, 5));
                    return loop.reply(true, "cast");
                });
                tick(loop, context);
                assertThat(result.join().success()).isTrue();
                assertThat(data.gameObjects).anyMatch(object -> object.getType() == PrefabType.FireShot && object.getMaster() == side);
            }
            for (int frame = 0; frame < 10; frame++) tick(loop, context);
            assertThat(loop.getLastSnapshotObjects()).anyMatch(object -> object.prefab().equals("FireShot"));
            var clear = loop.command("clear", () -> loop.clear(Master.None));
            tick(loop, context);
            assertThat(clear.join().success()).isTrue();
            assertThat(data.gameObjects).hasSize(3);
            assertThat(data.gameObjectsToAdd).isEmpty();
            assertThat(context.getResultChecker().checkResult()).isFalse();
            verifyNoInteractions(mmr, users);
        } finally { session.getPingChecker().close(); loop.close(); }
    }

    private static void tick(PlaygroundLoop loop, GameContext context) throws Exception {
        context.incrementFrameNum();
        var update = WordOnlineLoop.class.getDeclaredMethod("update");
        update.setAccessible(true);
        update.invoke(loop);
    }
}
