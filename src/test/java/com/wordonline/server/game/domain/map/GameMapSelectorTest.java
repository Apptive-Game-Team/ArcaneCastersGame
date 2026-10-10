package com.wordonline.server.game.domain.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.config.GameMapProperties.Selection;
import com.wordonline.server.game.domain.SessionType;

class GameMapSelectorTest {

    private final RandomGenerator random = mock(RandomGenerator.class);

    @Test
    void aFixedRandomSourceGivesBothMaps() {
        GameMapSelector selector = selector(Selection.RANDOM);

        when(random.nextBoolean()).thenReturn(true);
        assertThat(selector.choose(SessionType.PVP)).isEqualTo(GameMap.RIVER);

        when(random.nextBoolean()).thenReturn(false);
        assertThat(selector.choose(SessionType.PVP)).isEqualTo(GameMap.DEFAULT);
    }

    @Test
    void practiceMatchesAreRolledLikePvp() {
        when(random.nextBoolean()).thenReturn(true);

        assertThat(selector(Selection.RANDOM).choose(SessionType.Practice)).isEqualTo(GameMap.RIVER);
    }

    @Test
    void forcingTheDefaultMapIgnoresTheRandomSource() {
        assertThat(selector(Selection.DEFAULT).choose(SessionType.PVP)).isEqualTo(GameMap.DEFAULT);
        verify(random, never()).nextBoolean();
    }

    @Test
    void forcingTheRiverMapIgnoresTheRandomSource() {
        assertThat(selector(Selection.RIVER).choose(SessionType.PVP)).isEqualTo(GameMap.RIVER);
        assertThat(selector(Selection.RIVER).choose(SessionType.Practice)).isEqualTo(GameMap.RIVER);
        verify(random, never()).nextBoolean();
    }

    @Test
    void pveAlwaysGetsTheDefaultMapEvenWhenTheRiverIsForced() {
        when(random.nextBoolean()).thenReturn(true);

        assertThat(selector(Selection.RANDOM).choose(SessionType.PVE)).isEqualTo(GameMap.DEFAULT);
        assertThat(selector(Selection.RIVER).choose(SessionType.PVE)).isEqualTo(GameMap.DEFAULT);
    }

    @Test
    void theSelectionDefaultsToRandom() {
        assertThat(new GameMapProperties(null).selection()).isEqualTo(Selection.RANDOM);
    }

    @Test
    void theDefaultMapHasNoTerrainAndTheRiverMapHasTheRiver() {
        assertThat(GameMap.DEFAULT.terrain()).isSameAs(Terrain.NONE);
        assertThat(GameMap.RIVER.terrain()).isSameAs(Terrain.RIVER);
    }

    private GameMapSelector selector(Selection selection) {
        return new GameMapSelector(new GameMapProperties(selection), random);
    }
}
