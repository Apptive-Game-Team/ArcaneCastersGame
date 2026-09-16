package com.wordonline.server.deck.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// One deck_cards row: the magic on the card, and how many copies of it the deck holds.
@Getter
@AllArgsConstructor
public class CardsDto {
    private final long id;
    private final String name;
    public int count;
}
