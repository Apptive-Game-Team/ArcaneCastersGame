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

    private void validateDeckSnapshot(List<Long> magicIds) {
        if (magicIds.size() != 15) {
            throw new IllegalArgumentException("Deck must contain exactly 15 cards");
        }
        boolean exceedsCopyLimit = magicIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream()
                .anyMatch(count -> count > 3);
        if (exceedsCopyLimit) {
            throw new IllegalArgumentException("Deck contains more than three copies of a magic");
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
