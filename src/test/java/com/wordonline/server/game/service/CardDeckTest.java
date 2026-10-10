package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.dto.CardInfoDto;

class CardDeckTest {

    private static final List<Long> LARGE_DECK = LongStream.rangeClosed(1, 1_000).boxed().toList();

    // One card a second for 30 s, then fever halves the interval: 30 + 60 cards.
    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void drawsTheSameCardsInAMinuteWithFeverHalfwayAtAnyTickRate(int tickRate) {
        CardDeck deck = new CardDeck(LARGE_DECK, 1L);
        PlayerData player = new PlayerData(null);
        float deltaTime = 1f / tickRate;
        int drawn = 0;

        for (int frame = 1; frame <= 60 * tickRate; frame++) {
            if (frame == 30 * tickRate + 1) {
                deck.fever();
            }
            deck.drawCard(player, new CardInfoDto(), deltaTime);
            // Played straight away, so the hand limit never holds a draw back.
            drawn += player.cards.size();
            player.cards.clear();
        }

        assertThat(drawn).isEqualTo(90);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void drawsTheFirstCardAfterOneSecond(int tickRate) {
        CardDeck deck = new CardDeck(LARGE_DECK, 1L);
        PlayerData player = new PlayerData(null);
        float deltaTime = 1f / tickRate;

        for (int frame = 1; frame < tickRate; frame++) {
            deck.drawCard(player, new CardInfoDto(), deltaTime);
        }
        assertThat(player.cards).isEmpty();

        deck.drawCard(player, new CardInfoDto(), deltaTime);
        assertThat(player.cards).hasSize(1);
    }

    // Fever shortens the interval mid-wait: the time already waited counts toward the new one.
    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void keepsTheTimeAlreadyWaitedWhenFeverStarts(int tickRate) {
        CardDeck deck = new CardDeck(LARGE_DECK, 1L);
        PlayerData player = new PlayerData(null);
        float deltaTime = 1f / tickRate;

        // 0.3 s into the first interval, then fever: the next card is due 0.2 s later.
        int framesBeforeFever = tickRate * 3 / 10;
        for (int frame = 0; frame < framesBeforeFever; frame++) {
            deck.drawCard(player, new CardInfoDto(), deltaTime);
        }
        deck.fever();
        int framesUntilDraw = 0;
        while (player.cards.isEmpty()) {
            deck.drawCard(player, new CardInfoDto(), deltaTime);
            framesUntilDraw++;
        }

        assertThat(framesUntilDraw).isEqualTo(tickRate / 5);
    }
}
