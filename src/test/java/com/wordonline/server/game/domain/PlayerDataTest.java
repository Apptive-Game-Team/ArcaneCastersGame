package com.wordonline.server.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// PlayerData used to be locked because casts arrived on inbound and bot threads. They now arrive as
// game actions drained by the loop thread, so what is worth pinning is the accounting itself: one
// card is one magic, a cast is rejected rather than half applied, and mana never goes negative.
class PlayerDataTest {

    private static final long LEAFAIR = 34L;
    private static final long EMBER = 12L;

    private PlayerData newPlayer() {
        return new PlayerData(null);
    }

    @Test
    void useCardSpendsTheMagicCostAndRemovesOneCopy() {
        PlayerData player = newPlayer();
        player.mana = 20;
        player.addCard(LEAFAIR);
        player.addCard(LEAFAIR);

        assertThat(player.useCard(LEAFAIR, 10)).isTrue();
        assertThat(player.mana).isEqualTo(10);
        assertThat(player.cards).containsExactly(LEAFAIR);
    }

    @Test
    void useCardRejectsAMagicTheHandDoesNotHoldWithoutChargingAnything() {
        PlayerData player = newPlayer();
        player.mana = 20;
        player.addCard(LEAFAIR);

        assertThat(player.useCard(EMBER, 10)).isFalse();
        assertThat(player.mana).isEqualTo(20);
        assertThat(player.cards).containsExactly(LEAFAIR);
    }

    @Test
    void useCardRejectsACostTheManaPoolCannotCover() {
        PlayerData player = newPlayer();
        player.mana = 5;
        player.addCard(LEAFAIR);

        assertThat(player.useCard(LEAFAIR, 10)).isFalse();
        assertThat(player.mana).isEqualTo(5);
        assertThat(player.cards).containsExactly(LEAFAIR);
    }

    @Test
    void spendManaNeverOverdraws() {
        PlayerData player = newPlayer();
        player.mana = 10;

        assertThat(player.spendMana(10)).isTrue();
        assertThat(player.spendMana(10)).isFalse();
        assertThat(player.mana).isZero();
    }

    @Test
    void addManaClampsToTheMaximum() {
        PlayerData player = newPlayer();

        player.addMana(30, 20);

        assertThat(player.mana).isEqualTo(20);
    }

    @Test
    void addCardStopsAtTheHandLimit() {
        PlayerData player = newPlayer();

        for (int i = 0; i < PlayerData.MAX_CARD_NUM; i++) {
            assertThat(player.addCard(LEAFAIR)).isTrue();
        }

        assertThat(player.addCard(LEAFAIR)).isFalse();
        assertThat(player.cards).hasSize(PlayerData.MAX_CARD_NUM);
    }
}
