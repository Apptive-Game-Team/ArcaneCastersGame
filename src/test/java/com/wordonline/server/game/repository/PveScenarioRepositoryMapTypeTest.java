package com.wordonline.server.game.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.wordonline.server.game.domain.map.GameMap;

/** Reads stages.map_type through scenarios, against the shared H2 fixtures. */
class PveScenarioRepositoryMapTypeTest {

    private PveScenarioRepository repository;

    @BeforeEach
    void setUp() {
        JdbcClient jdbcClient = MapFixtureDatabase.create();
        repository = new PveScenarioRepository(jdbcClient);
    }

    @Test
    void readsTheMapOfTheStageTheScenarioBelongsTo() {
        assertThat(repository.findMapType(1L)).contains(GameMap.FOREST);
        assertThat(repository.findMapType(2L)).contains(GameMap.FORTRESS);
        assertThat(repository.findMapType(3L)).contains(GameMap.GATE);
    }

    @Test
    void aStageThatLeavesMapTypeOutTakesTheColumnDefault() {
        assertThat(repository.findMapType(4L)).contains(GameMap.GRASSLAND);
    }

    @Test
    void aStageWithoutAnAdventureStillHasItsOwnMap() {
        assertThat(repository.findMapType(5L)).contains(GameMap.GRASSLAND);
    }

    @Test
    void aScenarioWithoutAStageOrAnUnknownScenarioHasNoMap() {
        assertThat(repository.findMapType(6L)).isEmpty();
        assertThat(repository.findMapType(999L)).isEmpty();
    }

    @Test
    void twoStagesOfOneAdventureHaveTheirOwnMaps() {
        assertThat(repository.findMapType(7L)).contains(GameMap.FOREST);
        assertThat(repository.findMapType(8L)).contains(GameMap.RIVER);
    }
}
