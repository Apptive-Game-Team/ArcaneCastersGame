package com.wordonline.server.deck.service;

import com.wordonline.server.deck.dto.*;
import com.wordonline.server.deck.repository.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class DeckService {

    private final DeckRepository deckRepository;

    /** The deck as it is dealt: one entry per physical card, each entry a magics.id. */
    @Transactional(readOnly = true)
    public List<Long> getSelectedCards(long userId) {
        return mapToMagicIds(
                deckRepository.getSelectedDeck(userId)
        );
    }

    @Transactional(readOnly = true)
    public List<Long> getParticipantCards(long participantId) {
        return getSelectedCards(participantId);
    }

    public List<CardDto> getDeckCards(long deckId) {
        return mapToCardDto(
                deckRepository.getDeck(deckId)
        );
    }

    @Transactional(readOnly = true)
    public List<CardDto> getParticipantDeckCards(long participantId) {
        return getDeckCards(
                deckRepository.getSelectedDeckId(participantId)
                        .orElseThrow(() -> new IllegalArgumentException("Deck Not Found"))
        );
    }

    private List<CardDto> mapToCardDto(List<CardsDto> cardsDtos) {
        List<CardDto> cardDtos = new ArrayList<>();
        cardsDtos.forEach(
                cardsDto ->
                        cardDtos.addAll(
                                Stream.generate(() -> new CardDto(cardsDto))
                                        .limit(cardsDto.getCount())
                                        .toList()
                        )
        );
        return cardDtos;
    }

    private List<Long> mapToMagicIds(List<CardsDto> cardDtos) {
        List<Long> magicIds = new ArrayList<>();
        cardDtos.forEach(
                cardsDto ->
                        magicIds.addAll(
                                Stream.generate(cardsDto::getId)
                                        .limit(cardsDto.getCount())
                                        .toList()
                        )
            );
        return magicIds;
    }
}
