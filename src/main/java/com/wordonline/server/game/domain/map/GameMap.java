package com.wordonline.server.game.domain.map;

/**
 * The map kind of one match, sent to the client as {@code mapType}. Only {@code RIVER} has
 * terrain (a river across the middle with two bridges); the other four have none and only name
 * the look the client draws. PVP, practice and bot matches get {@code GRASSLAND} or {@code RIVER};
 * {@code FORTRESS}, {@code GATE} and {@code FOREST} come only from {@code adventures.map_type} for
 * PVE. {@link GameMapSelector} decides it once, before the session is created.
 */
public enum GameMap {
    GRASSLAND,
    RIVER,
    FORTRESS,
    GATE,
    FOREST;

    public Terrain terrain() {
        return this == RIVER ? Terrain.RIVER : Terrain.NONE;
    }
}
