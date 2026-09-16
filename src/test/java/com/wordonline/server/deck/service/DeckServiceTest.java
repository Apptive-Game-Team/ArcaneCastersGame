package com.wordonline.server.deck.service;

import com.wordonline.server.deck.dto.CardsDto;
import com.wordonline.server.deck.repository.DeckRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeckServiceTest {

    @Test
    void loadsBotDeckFromNegativeUserSelectedDeck() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        when(repository.getSelectedDeck(-7)).thenReturn(List.of(new CardsDto(34, "leafair", 2)));

        assertThat(service.getParticipantCards(-7)).containsExactly(34L, 34L);
        verify(repository).getSelectedDeck(-7);
    }

    @Test
    void usesAndValidatesSessionDeckSnapshotInsteadOfSelectedDeck() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        List<Long> snapshot = List.of(1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L, 3L,
                4L, 4L, 4L, 5L, 5L, 5L);
        when(repository.getMagics(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(List.of(
                new CardsDto(1, "one", 1), new CardsDto(2, "two", 1),
                new CardsDto(3, "three", 1), new CardsDto(4, "four", 1),
                new CardsDto(5, "five", 1)));

        assertThat(service.getParticipantCards(7, snapshot)).containsExactlyElementsOf(snapshot);
        verify(repository).getMagics(List.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void rejectsSnapshotThatExceedsCopyLimit() {
        DeckService service = new DeckService(mock(DeckRepository.class));
        List<Long> snapshot = List.of(1L, 1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L,
                3L, 4L, 4L, 4L, 5L, 5L);

        assertThatThrownBy(() -> service.getParticipantCards(7, snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three copies");
    }
}
