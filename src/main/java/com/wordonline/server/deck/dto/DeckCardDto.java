package com.wordonline.server.deck.dto;

public record DeckCardDto(
        long deckId,
        long magicId,
        int count,
        String deckName,
        String magicName
) {
}
