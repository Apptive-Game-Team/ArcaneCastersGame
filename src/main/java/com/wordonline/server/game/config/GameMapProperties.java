package com.wordonline.server.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which arena a match gets. {@code RANDOM} (the default) lets {@code GameMapSelector} pick
 * between the maps for every match; {@code DEFAULT} or {@code RIVER} forces one, which is how a
 * maintainer reproduces a map locally. Property: {@code game.map.selection}, environment
 * variable {@code GAME_MAP_SELECTION}. PVE scenarios always use the default arena, forced or not.
 */
@ConfigurationProperties(prefix = "game.map")
public record GameMapProperties(Selection selection) {

    public enum Selection {
        RANDOM,
        DEFAULT,
        RIVER
    }

    public GameMapProperties {
        selection = selection == null ? Selection.RANDOM : selection;
    }
}
