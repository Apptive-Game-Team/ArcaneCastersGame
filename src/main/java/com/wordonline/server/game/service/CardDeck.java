package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.dto.CardInfoDto;
import com.wordonline.server.game.util.IntervalTimer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

// CardDeck class to manage the deck of cards. A card is one magic, held as its magics.id.
@Slf4j
public class CardDeck {

    private static final float CARD_DRAW_INTERVAL = 1;
    private final Stat cardDrawInterval = new Stat(CARD_DRAW_INTERVAL);
    // Kept across fever, which shortens the interval: the time already waited counts toward the
    // shorter interval instead of the draw phase jumping.
    private final IntervalTimer drawTimer = new IntervalTimer();
    private final Queue<Long> cards;

    public CardDeck(List<Long> cards) {
        this(cards, System.nanoTime());
    }

    public CardDeck(List<Long> cards, long shuffleSeed) {
        List<Long> shuffledCards = new ArrayList<>(cards);
        Collections.shuffle(shuffledCards, new Random(shuffleSeed));
        this.cards = new ConcurrentLinkedDeque<>(shuffledCards);
        log.debug("[CardDeck] shuffled {} cards with seed={}", shuffledCards.size(), shuffleSeed);
    }

    public void returnCard(long magicId) {
        this.cards.add(magicId);
    }

    // called every frame; draws once per cardDrawInterval of game time
    public void drawCard(PlayerData player, CardInfoDto cardInfoDto, float deltaTime) {
        int draws = drawTimer.advance(deltaTime, cardDrawInterval.total());
        for (int i = 0; i < draws; i++) {
            drawOne(player, cardInfoDto);
        }
    }

    // random pick card and update to Player Data, Frame Info Dto
    private void drawOne(PlayerData player, CardInfoDto cardInfoDto) {
        if (cards.isEmpty() || player.cards.size() >= PlayerData.MAX_CARD_NUM)
            return;

        long card = cards.remove();

        if (player.addCard(card)){ // Added
            cardInfoDto.addCard(card);
        }
    }

    // only use for debug
    public void setCards(List<Long> cards) {
        this.cards.clear();
        this.cards.addAll(cards);
    }

    public void fever() {
        cardDrawInterval.addPercent(-0.5f);
    }
}
