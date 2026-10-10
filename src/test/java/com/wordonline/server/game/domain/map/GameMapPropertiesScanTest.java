package com.wordonline.server.game.domain.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.wordonline.server.game.config.GameMapProperties;

/** Pins the property name that forces a map: {@code game.map.selection}, set by {@code GAME_MAP_SELECTION}. */
@SpringJUnitConfig(classes = GameMapPropertiesScanTest.ScanConfig.class)
@TestPropertySource(properties = "game.map.selection=river")
class GameMapPropertiesScanTest {

    @Autowired
    private GameMapProperties properties;

    @Test
    void theScanBindsTheSelectionCaseInsensitively() {
        assertThat(properties.selection()).isEqualTo(GameMapProperties.Selection.RIVER);
    }

    @Configuration
    @ConfigurationPropertiesScan("com.wordonline.server.game.config")
    static class ScanConfig {
    }
}
