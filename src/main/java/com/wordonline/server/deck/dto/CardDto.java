package com.wordonline.server.deck.dto;

// One physical card in a deck. id is magics.id and name is magics.name.
public record CardDto(
        long id,
        String name
) {
    public CardDto(CardsDto cardsDto) {
        this(cardsDto.getId(), cardsDto.getName());
    }
}
