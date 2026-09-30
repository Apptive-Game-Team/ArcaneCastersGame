package com.wordonline.server.game.domain.magic;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * A magic that leaves lasting bodies on the field, and what it leaves.
 *
 * <p>The bot needs to price a board it did not build: to stay weaker than the player, the
 * hospitality bot has to know what the player's units cost to summon. The only honest source for
 * "which magic produces this prefab" is the magic that produces it, so the link is declared here
 * rather than kept as data beside the objects. A name match does not work - {@code
 * ember_spirit_swarm} puts down {@code ember_spirit} - and a hand-maintained column would be one
 * more thing to forget when a unit is added.
 *
 * <p>Implemented by the summoning families only. Shots, drops and explosions create objects too,
 * but those are in flight rather than standing on the field, and counting them as board presence
 * would price a passing projectile like a unit.
 */
public interface ObjectSummoningMagic {

    /** The prefab this magic puts on the field. */
    PrefabType summonedPrefab();

    /**
     * How many bodies one cast puts down. The per-unit price is the recipe's cost divided by this,
     * so a swarm on the field adds up to what the swarm cost rather than to that cost times its
     * size.
     */
    default int summonedQuantity() {
        return 1;
    }

    /**
     * Whether the bodies appear in the air. Flyers may be placed over anything, and cast
     * placement does not check them for overlap.
     */
    default boolean summonsAirborne() {
        return false;
    }
}
