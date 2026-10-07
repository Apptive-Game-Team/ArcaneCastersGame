package com.wordonline.server.game.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.wordonline.server.game.domain.map.GameMap;

/** Reads adventures.map_type through scenarios and stages, against the shared H2 fixtures. */
class PveScenarioRepositoryMapTypeTest {

    private PveScenarioRepository repository;

    @BeforeEach
    void setUp() {
        JdbcClient jdbcClient = MapFixtureDatabase.create();
        repository = new PveScenarioRepository(jdbcClient);
    }

    @Test
    void readsTheMapOfTheAdventureTheScenarioBelongsTo() {
        assertThat(repository.findMapType(1L)).contains(GameMap.FOREST);
        assertThat(repository.findMapType(2L)).contains(GameMap.FORTRESS);
        assertThat(repository.findMapType(3L)).contains(GameMap.GATE);
    }

    @Test
    void anAdventureThatLeavesMapTypeOutTakesTheColumnDefault() {
        assertThat(repository.findMapType(4L)).contains(GameMap.GRASSLAND);
    }

    @Test
    void aScenarioWithoutAnAdventureOrStageHasNoMap() {
        assertThat(repository.findMapType(5L)).isEmpty();
        assertThat(repository.findMapType(6L)).isEmpty();
        assertThat(repository.findMapType(999L)).isEmpty();
    }
}
