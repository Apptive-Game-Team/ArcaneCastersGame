package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotTier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * How well a bot plays: which {@link BotCapability capabilities} it has and how much noise it adds
 * to its own judgement.
 *
 * <p>This is the one place a tier's strength is defined. Each tier knows everything the tier below
 * it knows, plus a little more, so moving a capability between tiers is a one-line change here.
 *
 * @param noiseAmplitude how far, as a fraction, each candidate's score may be randomly scaled before
 *                       the best is picked; 0 always takes the best play
 * @param capabilities   what this bot knows how to do
 */
public record BotSkill(double noiseAmplitude, Set<BotCapability> capabilities) {

    private static final Set<BotCapability> COMBOS =
            EnumSet.of(BotCapability.SEED_SPIRIT_COMBO, BotCapability.MOB_CLUSTER_COMBO);

    public BotSkill {
        capabilities = capabilities.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(BotCapability.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public boolean has(BotCapability capability) {
        return capabilities.contains(capability);
    }

    /** A copy that also knows the given capabilities. */
    public BotSkill with(BotCapability... added) {
        Set<BotCapability> next = EnumSet.noneOf(BotCapability.class);
        next.addAll(capabilities);
        next.addAll(List.of(added));
        return new BotSkill(noiseAmplitude, next);
    }

    /** A copy that has forgotten the given capabilities. */
    public BotSkill without(BotCapability... removed) {
        Set<BotCapability> next = EnumSet.noneOf(BotCapability.class);
        next.addAll(capabilities);
        List.of(removed).forEach(next::remove);
        return new BotSkill(noiseAmplitude, next);
    }

    /**
     * The skill each tier plays with.
     *
     * <p>HOSPITALITY knows nothing beyond the base scorer on purpose: it plays to lose through
     * {@link HospitalityDirector}, and every capability here exists to win. INTRO keeps exactly the
     * play every ordinary tier had before capabilities existed.
     */
    public static BotSkill forTier(BotTier tier) {
        return switch (tier) {
            case HOSPITALITY -> new BotSkill(0.0, Set.of());
            case INTRO -> new BotSkill(0.50, COMBOS);
            case BEGINNER -> new BotSkill(0.30, COMBOS)
                    .with(BotCapability.STRONGEST_LANE);
            case INTERMEDIATE -> forTier(BotTier.BEGINNER)
                    .withNoise(0.18)
                    .with(BotCapability.LETHAL, BotCapability.SUMMON_STRENGTH, BotCapability.THREAT_STRENGTH);
            case ADVANCED -> forTier(BotTier.INTERMEDIATE)
                    .withNoise(0.08)
                    .with(BotCapability.ALLY_COVER, BotCapability.MANA_DISCIPLINE, BotCapability.ROLE_PLACEMENT);
            case ELITE -> forTier(BotTier.ADVANCED)
                    .withNoise(0.0)
                    .with(BotCapability.LEAD_TARGETING);
        };
    }

    private BotSkill withNoise(double amplitude) {
        return new BotSkill(amplitude, capabilities);
    }
}
