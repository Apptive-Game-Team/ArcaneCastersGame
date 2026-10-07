package com.wordonline.server.game.domain.map;

/**
 * The arena layout of one match. {@code DEFAULT} is the open arena; {@code RIVER} adds a river
 * across the middle with two bridges. Which one a match gets is decided once, by
 * {@link GameMapSelector}, when the loop is initialized.
 */
public enum GameMap {
    DEFAULT,
    RIVER;

    public Terrain terrain() {
        return this == RIVER ? Terrain.RIVER : Terrain.NONE;
    }
}
