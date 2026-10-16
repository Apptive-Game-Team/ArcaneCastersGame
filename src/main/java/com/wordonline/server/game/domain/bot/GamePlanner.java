package com.wordonline.server.game.domain.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.repository.TagRepository;

/**
 * Saves for a wave of fighters and releases it on one spot, instead of spending each tick's mana.
 *
 * <p>The value scorer picks the best card per mana every tick, so it spends mana as soon as it has
 * any and never casts what it cannot pay for at once. With a {@link CardRole#FIGHTER} of at least
 * {@value #WAVE_CARD_COST} mana in hand, and a deck built for it ({@link DeckProfile#wavesPayOff}),
 * the bot saves until every fighter in hand is affordable together, then puts them down one tick
 * after another on one spot, so they arrive at once instead of being picked off one by one. A
 * fighter of ours still standing near our base is a wave being released; the next one joins it.
 * When the deck is unknown, a dear fighter in hand is enough.
 *
 * <p>The brain only asks while calm and not overdue; pressure goes to the value scorer. Saving
 * stops {@value #CAP_MARGIN} mana short of the cap, since income past the cap is lost.
 *
 * <p>Stateless: whether a wave is being released is read off the board, so asking twice in one
 * tick, as the LLM decision sources do, changes nothing.
 */
final class GamePlanner {

    static final String WAVE_RULE = "plan.wave";

    /**
     * A fighter at least this dear is worth saving for. At 35 the bot saved for thunder_spirit on
     * the Skies deck and cast chain_lightning at the enemy base 5 times a game instead of 12, and
     * lost 23 of 25; the 80 mana fighters of a big deck are what saving pays off for.
     */
    static final int WAVE_CARD_COST = 50;
    /** Our own fighter this close to our base is a wave still being released. */
    static final double STAGING_DISTANCE = 5.0;
    /** Saving stops this far below the mana cap. */
    static final int CAP_MARGIN = 10;

    enum Kind { NONE, SAVE, WAVE }

    record Plan(Kind kind, Optional<ScoredPlay> play) {
        static final Plan NONE = new Plan(Kind.NONE, Optional.empty());
        static final Plan SAVE = new Plan(Kind.SAVE, Optional.empty());

        boolean saving() {
            return kind == Kind.SAVE;
        }
    }

    private GamePlanner() {
    }

    /**
     * @param deck      what the whole deck is built for, or {@link DeckProfile#UNKNOWN}
     * @param placement where the brain would put a summon right now
     */
    static Plan plan(ThinkContext context, int maxMana, Parameters parameters, DeckProfile deck,
                     Function<Magic, Vector3> placement) {
        if (deck.known() && !deck.wavesPayOff()) {
            return Plan.NONE;
        }
        List<Fighter> fighters = context.hand().stream()
                .filter(magic -> magic instanceof ObjectSummoningMagic && CardRole.of(magic) == CardRole.FIGHTER)
                .map(magic -> new Fighter(magic, context.spellStats().manaCost(magic)))
                .filter(fighter -> fighter.cost() > 0 && fighter.cost() < BotSpellStats.UNKNOWN_MANA_COST)
                .toList();
        if (fighters.stream().noneMatch(fighter -> fighter.cost() >= WAVE_CARD_COST)) {
            return Plan.NONE;
        }
        int mana = context.mana();
        int total = fighters.stream().mapToInt(Fighter::cost).sum();
        int dearest = fighters.stream().mapToInt(Fighter::cost).max().orElse(0);
        int cap = maxMana > 0 ? maxMana - CAP_MARGIN : Integer.MAX_VALUE;
        int target = Math.max(dearest, Math.min(total, cap));
        Optional<BotVisibleObject> forming = formingWave(context, parameters);
        Optional<Fighter> next = fighters.stream()
                .filter(fighter -> fighter.cost() <= mana)
                .max(Comparator.comparingInt(Fighter::cost));
        if (next.isPresent() && (mana >= target || forming.isPresent())) {
            Fighter fighter = next.get();
            Vector3 spot = forming.map(BotVisibleObject::position).orElseGet(() -> placement.apply(fighter.magic()));
            return new Plan(Kind.WAVE, Optional.of(context.scored(fighter.magic(), new Vector3(spot),
                    fighter.cost(), 1.0, WAVE_RULE,
                    (forming.isPresent() ? "Joins the wave forming near our base: " : "Releases a wave at mana "
                            + mana + ": ") + fighter.magic().name + ".")));
        }
        if (mana >= cap) {
            return Plan.NONE;
        }
        return Plan.SAVE;
    }

    /** Our moving unit nearest our base, when it still stands within {@link #STAGING_DISTANCE} of it. */
    private static Optional<BotVisibleObject> formingWave(ThinkContext context, Parameters parameters) {
        return context.objects().stream()
                .filter(object -> object.master() == context.botSide())
                .filter(BotVisibleObject::mob)
                .filter(BotVisibleObject::targetable)
                .filter(object -> object.type() != PrefabType.Player)
                .filter(object -> moves(object.type(), parameters))
                .filter(object -> object.position().distance(context.playerPos()) <= STAGING_DISTANCE)
                .min(Comparator.comparingDouble(object -> object.position().distance(context.playerPos())));
    }

    private static boolean moves(PrefabType type, Parameters parameters) {
        return type != null && parameters.getValueOrDefault(
                TagRepository.toGameObjectName(type), ParameterKey.SPEED.dbName(), 0.0) > 0;
    }

    private record Fighter(Magic magic, int cost) {
    }
}
