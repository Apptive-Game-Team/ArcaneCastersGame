package com.wordonline.server.game.domain;

import com.wordonline.server.game.service.ManaCharger;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// This class is used to store player data
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class PlayerData {
    public final static int MAX_CARD_NUM = 6;

    public final ManaCharger manaCharger;

    // Mutated only by the loop thread, so the check-then-act methods below need no locking, and
    // the bot reads a copy taken by BotEye.observe rather than these fields.
    public int mana = 0;
    public int hp;
    // A card in hand is one magic, held as its magics.id.
    public List<Long> cards = new ArrayList<>();

    // charge mana up to max
    public void addMana(int delta, int max) {
        mana = Math.min(mana + delta, max);
    }

    public boolean spendMana(int cost) {
        if (mana < cost) {
            return false;
        }
        mana -= cost;
        return true;
    }

    // validate and add card
    public boolean addCard(long magicId) {
        if (MAX_CARD_NUM >= cards.size() + 1) {
            cards.add(magicId);
            return true;
        }
        return false;
    }

    // The mana cost belongs to the magic, so the caller reads it and passes it in; PlayerData only
    // decides whether the hand holds the card and whether the pool covers the cost.
    public boolean validCardUse(long magicId, int manaCost) {
        if (!cards.contains(magicId)) {
            log.trace("hand: {}, trying magic {}", cards, magicId);
            return false;
        }
        return manaCost <= mana;
    }

    // validate and use one card
    public boolean useCard(long magicId, int manaCost) {
        if (!validCardUse(magicId, manaCost)) {
            return false;
        }

        cards.remove(Long.valueOf(magicId));
        mana -= manaCost;
        return true;
    }
}
