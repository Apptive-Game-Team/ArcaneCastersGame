package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedSpiritVineSummonerTest {

    private static final float RADIUS = 7f;

    @Test
    void summonsAVineOwnedByTheBuildingAtAnAlliedSeedSpirit() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        Vector3 seedSpiritPosition = new Vector3(3f, 0f, 2f);
        GameObject seedSpirit = objectOf(PrefabType.SeedSpirit, Master.LeftPlayer, seedSpiritPosition);
        when(gameContext.overlapSphereAll(building, RADIUS)).thenReturn(List.of(seedSpirit));
        clearInvocations(gameContext);

        new SeedSpiritVineSummoner(building, RADIUS).update();

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(spawned.capture());
        GameObject vine = spawned.getValue();
        assertThat(vine.getType()).isEqualTo(PrefabType.Vine);
        assertThat(vine.getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(vine.getPosition().getX()).isEqualTo(3f);
        assertThat(vine.getPosition().getZ()).isEqualTo(2f);
    }

    @Test
    void ignoresEnemySeedSpiritsAndOtherUnits() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        GameObject enemySeedSpirit = objectOf(PrefabType.SeedSpirit, Master.RightPlayer, new Vector3(1f, 0f, 0f));
        GameObject alliedVineSpirit = objectOf(PrefabType.VineSpirit, Master.LeftPlayer, new Vector3(2f, 0f, 0f));
        when(gameContext.overlapSphereAll(building, RADIUS)).thenReturn(List.of(enemySeedSpirit, alliedVineSpirit));
        clearInvocations(gameContext);

        new SeedSpiritVineSummoner(building, RADIUS).update();

        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void skipsASeedSpiritAlreadyTransformedEarlierInTheSameTick() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        GameObject transformedSeedSpirit = objectOf(PrefabType.SeedSpirit, Master.LeftPlayer, new Vector3(1f, 0f, 0f));
        when(transformedSeedSpirit.isDestroyed()).thenReturn(true);
        when(gameContext.overlapSphereAll(building, RADIUS)).thenReturn(List.of(transformedSeedSpirit));
        clearInvocations(gameContext);

        new SeedSpiritVineSummoner(building, RADIUS).update();

        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    private static GameObject objectOf(PrefabType type, Master master, Vector3 position) {
        GameObject object = mock(GameObject.class);
        when(object.getType()).thenReturn(type);
        when(object.getMaster()).thenReturn(master);
        when(object.getPosition()).thenReturn(position);
        return object;
    }
}
