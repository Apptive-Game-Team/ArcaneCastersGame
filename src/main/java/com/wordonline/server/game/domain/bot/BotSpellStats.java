package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;

/**
 * Gameplay parameters the bot needs to evaluate one magic card before casting it.
 *
 * <p>A card is one magic, so mana cost is exactly what {@code MagicInputHandler} charges. Damage
 * and radius are still an approximation: they are read from the same parameter row the cast reads,
 * which does not describe every object the spell eventually spawns.
 */
public final class BotSpellStats {

    /**
     * Charged when a magic has no {@code mana_cost} row. Deliberately unaffordable so a card with
     * unknown pricing is skipped instead of being cast and rejected by the input handler.
     */
    static final int UNKNOWN_MANA_COST = 9_999;

    /** Used when a magic has no {@code damage} row, so blast scoring still ranks by coverage. */
    static final double UNKNOWN_DAMAGE = 8.0;

    /** Used when a magic has no {@code radius} row (for example a summon). */
    static final double UNKNOWN_BLAST_RADIUS = 0.5;

    private final Parameters parameters;

    public BotSpellStats(Parameters parameters) {
        this.parameters = parameters;
    }

    /** Mana the cast costs, matching what the input handler charges. */
    public int manaCost(Magic magic) {
        return (int) parameters.getValueOrDefault(key(magic), "mana_cost", UNKNOWN_MANA_COST);
    }

    /** Maximum distance from the caster the target position may be. */
    public double castRange(Magic magic) {
        return parameters.getValueOrDefault(key(magic), "range", 0.0);
    }

    /** Radius around the target position that the spell is expected to cover. */
    public double blastRadius(Magic magic) {
        return parameters.getValueOrDefault(key(magic), "radius", UNKNOWN_BLAST_RADIUS);
    }

    /** Expected damage applied to each covered target. */
    public double damagePerTarget(Magic magic) {
        double damage = parameters.getValueOrDefault(key(magic), "damage", UNKNOWN_DAMAGE);
        return damage > 0 ? damage : UNKNOWN_DAMAGE;
    }

    // The same key MagicInputHandler reads the cast with, so the bot never prices a cast
    // differently from the handler that charges it.
    private static String key(Magic magic) {
        return magic.name;
    }
}
