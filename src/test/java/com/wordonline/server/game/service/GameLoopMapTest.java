package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.config.GameMapProperties.Selection;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMapSelector;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

/** initializeLoop is where the map is chosen and its cells are spawned, for every loop. */
class GameLoopMapTest {

    private final GameContext gameContext = mock(GameContext.class);

    @Test
    void aRiverMatchSpawnsEightWaterAndTwelveBridgeObjectsAtTheCellCenters() {
        List<GameObject> spawned = start(Selection.RIVER, SessionType.PVP);

        List<GameObject> water = ofType(spawned, PrefabType.RiverWater);
        List<GameObject> bridges = ofType(spawned, PrefabType.RiverBridge);
        assertThat(water).hasSize(8);
        assertThat(bridges).hasSize(12);
        assertThat(water).allMatch(object -> object.getMaster() == Master.None);
        assertThat(bridges).allMatch(object -> object.getMaster() == Master.None);
        assertThat(water).extracting(GameObject::getPosition).containsExactlyInAnyOrderElementsOf(
                Terrain.RIVER.waterCells().stream().map(Terrain.Cell::center).toList());
        assertThat(bridges).extracting(GameObject::getPosition).containsExactlyInAnyOrderElementsOf(
                Terrain.RIVER.bridgeCells().stream().map(Terrain.Cell::center).toList());
        assertThat(water).extracting(GameObject::getPosition)
                .contains(new Vector3(8.5f, 0f, 0.5f), new Vector3(9.5f, 0f, 9.5f))
                .doesNotContain(new Vector3(8.5f, 0f, 2.5f));
        assertThat(bridges).extracting(GameObject::getPosition).contains(new Vector3(8.5f, 0f, 2.5f));
        verify(gameContext).setTerrain(Terrain.RIVER);
    }

    @Test
    void aDefaultMatchSpawnsNoTerrainObjects() {
        List<GameObject> spawned = start(Selection.DEFAULT, SessionType.PVP);

        assertThat(ofType(spawned, PrefabType.RiverWater)).isEmpty();
        assertThat(ofType(spawned, PrefabType.RiverBridge)).isEmpty();
        assertThat(ofType(spawned, PrefabType.Wall)).hasSize(1);
        verify(gameContext).setTerrain(Terrain.NONE);
    }

    @Test
    void aPveMatchKeepsTheDefaultMapEvenWhenTheRiverIsForced() {
        List<GameObject> spawned = start(Selection.RIVER, SessionType.PVE);

        assertThat(ofType(spawned, PrefabType.RiverWater)).isEmpty();
        verify(gameContext).setTerrain(Terrain.NONE);
    }

    @Test
    void aLoopNobodyConfiguredKeepsTheDefaultMap() {
        SessionObject sessionObject = sessionObject(SessionType.PVP);
        loop().init(sessionObject, () -> {
        });

        verify(gameContext).setTerrain(Terrain.NONE);
    }

    private List<GameObject> start(Selection selection, SessionType sessionType) {
        GameLoop loop = loop();
        loop.setGameMapSelector(new GameMapSelector(new GameMapProperties(selection)));
        loop.init(sessionObject(sessionType), () -> {
        });

        ArgumentCaptor<GameObject> captor = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, org.mockito.Mockito.atLeastOnce()).createGameObject(captor.capture());
        return captor.getAllValues();
    }

    private static List<GameObject> ofType(List<GameObject> objects, PrefabType type) {
        return objects.stream().filter(object -> object.getType() == type).toList();
    }

    private SessionObject sessionObject(SessionType sessionType) {
        return new SessionObject("session-1", 11L, 22L, mock(SimpMessagingTemplate.class), List.of(), List.of(),
                sessionType);
    }

    private GameLoop loop() {
        when(gameContext.getParameters()).thenReturn(mock(Parameters.class));
        return new GameLoop(mock(MmrService.class), mock(UserService.class), gameContext, mock(Parameters.class)) {
            @Override
            void update() {
            }
        };
    }
}
