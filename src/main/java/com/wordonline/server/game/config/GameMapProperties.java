package com.wordonline.server.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which map a PVP, practice or bot match gets. {@code RANDOM} (the default) lets
 * {@code GameMapSelector} pick between {@code GRASSLAND} and {@code RIVER} with equal chance for
 * every match; {@code GRASSLAND} or {@code RIVER} forces one, which is how a maintainer reproduces
 * a map locally. Property: {@code game.map.selection}, environment variable
 * {@code GAME_MAP_SELECTION}. PVE matches ignore it: their map comes from the stage.
 */
@ConfigurationProperties(prefix = "game.map")
public record GameMapProperties(Selection selection) {

    public enum Selection {
        RANDOM,
        GRASSLAND,
        RIVER
    }

    public GameMapProperties {
        selection = selection == null ? Selection.RANDOM : selection;
    }
}
