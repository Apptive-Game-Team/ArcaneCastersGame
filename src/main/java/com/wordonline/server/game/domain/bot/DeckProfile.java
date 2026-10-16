package com.wordonline.server.game.domain.bot;

import java.util.List;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;

/**
 * What the bot's whole deck is built for, read once per match from every card in it.
 *
 * <p>The hand holds six cards, so a plan read off the hand changes with every draw. The deck does
 * not. Measured on mirror decks against the plain scorer:
 * <ul>
 *   <li>A deck with {@value #WAVE_DECK_MIN_BIG} or more fighter cards of at least
 *       {@link GamePlanner#WAVE_CARD_COST} mana (two evil_ent and two cloud_dragon of 80) won 18
 *       and 21 of 24 by saving for them and releasing them together; the plain scorer never saves,
 *       so it barely cast them.</li>
 *   <li>A deck of cheap fighters lost by any saving: holding mana for them, the bot cast its
 *       10 mana spells 2 times a game instead of 6 and won 1 of 25. It plays as cast-on-sight.</li>
 * </ul>
 *
 * @param cards       cards in the deck, copies counted; 0 when the deck is unknown
 * @param bigFighters fighter cards of at least {@link GamePlanner#WAVE_CARD_COST} mana
 */
record DeckProfile(int cards, int bigFighters) {

    static final int WAVE_DECK_MIN_BIG = 3;
    static final DeckProfile UNKNOWN = new DeckProfile(0, 0);

    boolean known() {
        return cards > 0;
    }

    /** Whether saving for a wave of fighters pays off with this deck. */
    boolean wavesPayOff() {
        return bigFighters >= WAVE_DECK_MIN_BIG;
    }

    static DeckProfile of(List<Long> deckCards, DatabaseMagicParser magicParser, BotSpellStats spellStats) {
        if (deckCards == null || deckCards.isEmpty()) {
            return UNKNOWN;
        }
        int big = 0;
        for (Long magicId : deckCards) {
            Magic magic = magicId == null ? null : magicParser.getMagic(magicId);
            if (magic instanceof ObjectSummoningMagic && CardRole.of(magic) == CardRole.FIGHTER) {
                int cost = spellStats.manaCost(magic);
                if (cost >= GamePlanner.WAVE_CARD_COST && cost < BotSpellStats.UNKNOWN_MANA_COST) {
                    big++;
                }
            }
        }
        return new DeckProfile(deckCards.size(), big);
    }
}
