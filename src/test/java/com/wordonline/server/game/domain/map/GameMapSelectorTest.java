package com.wordonline.server.game.domain.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.config.GameMapProperties.Selection;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.repository.PveScenarioRepository;

class GameMapSelectorTest {

    private final RandomGenerator random = mock(RandomGenerator.class);
    private final PveScenarioRepository pveScenarioRepository = mock(PveScenarioRepository.class);

    @Test
    void aFixedRandomSourceGivesBothMaps() {
        GameMapSelector selector = selector(Selection.RANDOM);

        when(random.nextBoolean()).thenReturn(true);
        assertThat(selector.choose(SessionType.PVP, null)).isEqualTo(GameMap.RIVER);

        when(random.nextBoolean()).thenReturn(false);
        assertThat(selector.choose(SessionType.PVP, null)).isEqualTo(GameMap.GRASSLAND);
    }

    @Test
    void practiceMatchesAreRolledLikePvp() {
        when(random.nextBoolean()).thenReturn(true);
        assertThat(selector(Selection.RANDOM).choose(SessionType.Practice, null)).isEqualTo(GameMap.RIVER);

        when(random.nextBoolean()).thenReturn(false);
        assertThat(selector(Selection.RANDOM).choose(SessionType.Practice, null)).isEqualTo(GameMap.GRASSLAND);
    }

    @Test
    void randomNeverPicksAPveOnlyMap() {
        GameMapSelector selector = selector(Selection.RANDOM);

        for (boolean coin : new boolean[] {true, false}) {
            when(random.nextBoolean()).thenReturn(coin);
            assertThat(selector.choose(SessionType.PVP, null)).isIn(GameMap.GRASSLAND, GameMap.RIVER);
        }
    }

    @Test
    void forcingGrasslandIgnoresTheRandomSource() {
        assertThat(selector(Selection.GRASSLAND).choose(SessionType.PVP, null)).isEqualTo(GameMap.GRASSLAND);
        verify(random, never()).nextBoolean();
    }

    @Test
    void forcingTheRiverIgnoresTheRandomSource() {
        assertThat(selector(Selection.RIVER).choose(SessionType.PVP, null)).isEqualTo(GameMap.RIVER);
        assertThat(selector(Selection.RIVER).choose(SessionType.Practice, null)).isEqualTo(GameMap.RIVER);
        verify(random, never()).nextBoolean();
    }

    @Test
    void pveTakesTheMapOfTheAdventureWhateverTheSelectionSays() {
        when(random.nextBoolean()).thenReturn(true);
        when(pveScenarioRepository.findMapType(7L)).thenReturn(Optional.of(GameMap.FOREST));

        assertThat(selector(Selection.RANDOM).choose(SessionType.PVE, 7L)).isEqualTo(GameMap.FOREST);
        assertThat(selector(Selection.RIVER).choose(SessionType.PVE, 7L)).isEqualTo(GameMap.FOREST);
        verify(random, never()).nextBoolean();
    }

    @Test
    void pveWithoutAnAdventureOrScenarioGetsGrassland() {
        when(pveScenarioRepository.findMapType(8L)).thenReturn(Optional.empty());

        assertThat(selector(Selection.RIVER).choose(SessionType.PVE, 8L)).isEqualTo(GameMap.GRASSLAND);
        assertThat(selector(Selection.RIVER).choose(SessionType.PVE, null)).isEqualTo(GameMap.GRASSLAND);
    }

    @Test
    void nonPveSessionsDoNotReadTheDatabase() {
        selector(Selection.RANDOM).choose(SessionType.PVP, 7L);

        verifyNoInteractions(pveScenarioRepository);
    }

    @Test
    void theSelectionDefaultsToRandom() {
        assertThat(new GameMapProperties(null).selection()).isEqualTo(Selection.RANDOM);
    }

    @Test
    void theMapKindsAreTheFiveContractNamesInOrder() {
        assertThat(GameMap.values()).extracting(Enum::name)
                .containsExactly("GRASSLAND", "RIVER", "FORTRESS", "GATE", "FOREST");
        assertThat(Selection.values()).extracting(Enum::name).containsExactly("RANDOM", "GRASSLAND", "RIVER");
    }

    @Test
    void onlyTheRiverHasTerrain() {
        assertThat(GameMap.RIVER.terrain()).isSameAs(Terrain.RIVER);
        for (GameMap map : new GameMap[] {GameMap.GRASSLAND, GameMap.FORTRESS, GameMap.GATE, GameMap.FOREST}) {
            assertThat(map.terrain()).as(map.name()).isSameAs(Terrain.NONE);
        }
    }

    private GameMapSelector selector(Selection selection) {
        return new GameMapSelector(new GameMapProperties(selection), random, pveScenarioRepository);
    }
}
