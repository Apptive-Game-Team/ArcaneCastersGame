package com.wordonline.server.game.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RockObstacleSpawnLayoutTest {

    private static final float MIN_DISTANCE_FROM_PLAYER_SPAWN = 1.5f;

    @Test
    void layoutHasEightRocks() {
        assertThat(GameConfig.ROCK_OBSTACLE_POSITIONS).hasSize(8);
    }

    @Test
    void layoutIsSymmetricAboutTheArenaMidline() {
        List<Vector3> positions = GameConfig.ROCK_OBSTACLE_POSITIONS;

        for (Vector3 position : positions) {
            float mirroredX = 2 * GameConfig.X_MID - position.getX();
            assertThat(positions).anySatisfy(other -> {
                assertThat(other.getX()).isCloseTo(mirroredX, within(1e-6f));
                assertThat(other.getZ()).isCloseTo(position.getZ(), within(1e-6f));
            });
        }
    }

    @Test
    void everyRockStandsOnTheGroundInsideTheArena() {
        for (Vector3 position : GameConfig.ROCK_OBSTACLE_POSITIONS) {
            assertThat(position.getY()).isZero();
            assertThat(position.getX()).isBetween(0f, (float) GameConfig.WIDTH);
            assertThat(position.getZ()).isBetween(0f, (float) GameConfig.HEIGHT);
        }
    }

    @Test
    void noRockStandsNearAPlayerSpawn() {
        for (Vector3 position : GameConfig.ROCK_OBSTACLE_POSITIONS) {
            assertThat(position.distance(GameConfig.LEFT_PLAYER_POSITION)).isGreaterThan(MIN_DISTANCE_FROM_PLAYER_SPAWN);
            assertThat(position.distance(GameConfig.RIGHT_PLAYER_POSITION)).isGreaterThan(MIN_DISTANCE_FROM_PLAYER_SPAWN);
        }
    }

    @Test
    void everySessionCreatesEachRockOnceWithNoMaster() {
        GameContext gameContext = mock(GameContext.class);
        GameLoop loop = new GameLoop(mock(MmrService.class), mock(UserService.class),
                gameContext, mock(Parameters.class)) {
            @Override
            void update() {
            }
        };
        SessionObject sessionObject = new SessionObject(
                "session-1", 11L, 22L, mock(SimpMessagingTemplate.class), List.of(), List.of());

        loop.init(sessionObject, () -> {
        });

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, atLeastOnce()).createGameObject(created.capture());
        List<GameObject> rocks = created.getAllValues().stream()
                .filter(object -> object.getType() == PrefabType.RockObstacle)
                .toList();
        assertThat(rocks).hasSize(GameConfig.ROCK_OBSTACLE_POSITIONS.size());
        assertThat(rocks).allMatch(rock -> rock.getMaster() == Master.None);
        assertThat(rocks).extracting(rock -> rock.getPosition().getX())
                .containsExactlyInAnyOrderElementsOf(
                        GameConfig.ROCK_OBSTACLE_POSITIONS.stream().map(Vector3::getX).toList());
    }
}
