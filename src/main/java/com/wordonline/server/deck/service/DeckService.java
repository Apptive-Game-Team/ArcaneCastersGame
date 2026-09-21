package com.wordonline.server.deck.service;

import com.wordonline.server.deck.dto.*;
import com.wordonline.server.deck.repository.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class DeckService {

    // Same contract as the lobby's DeckValidator.DECK_CARD_COUNT: both servers must agree on
    // how many cards one deck holds, so change them together.
    private static final int DECK_CARD_COUNT = 15;

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

    @Transactional(readOnly = true)
    public List<Long> getParticipantCards(long participantId, List<Long> deckSnapshot) {
        if (deckSnapshot == null) {
            return getParticipantCards(participantId);
        }
        validateDeckSnapshot(deckSnapshot);
        return List.copyOf(deckSnapshot);
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

    @Transactional(readOnly = true)
    public List<CardDto> getCardsByMagicIds(List<Long> magicIds) {
        Map<Long, CardDto> cards = deckRepository.getMagics(magicIds.stream().distinct().toList()).stream()
                .map(CardDto::new)
                .collect(Collectors.toMap(CardDto::id, Function.identity()));
        if (cards.size() != magicIds.stream().distinct().count()) {
            throw new IllegalArgumentException("Deck contains an unknown magic");
        }
        return magicIds.stream().map(cards::get).toList();
    }

    // Card count and magic existence are all this server can check. There is no copy limit any
    // more - fifteen copies of one magic is a legal deck - and ownership is the lobby's check.
    private void validateDeckSnapshot(List<Long> magicIds) {
        if (magicIds.size() != DECK_CARD_COUNT) {
            throw new IllegalArgumentException(
                    "Deck must contain exactly " + DECK_CARD_COUNT + " cards");
        }
        getCardsByMagicIds(magicIds);
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
