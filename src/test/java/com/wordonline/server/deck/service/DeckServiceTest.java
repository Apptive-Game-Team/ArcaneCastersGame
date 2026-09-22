package com.wordonline.server.deck.service;

import com.wordonline.server.deck.dto.CardsDto;
import com.wordonline.server.deck.repository.DeckRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    // A magic-card deck has no copy limit: fifteen copies of one magic is a legal deck and the
    // game server must take it. This test is what keeps the old three-copy rule from coming back.
    @Test
    void acceptsSnapshotOfFifteenCopiesOfOneMagic() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        List<Long> snapshot = Collections.nCopies(15, 9L);
        when(repository.getMagics(List.of(9L))).thenReturn(List.of(new CardsDto(9, "nine", 1)));

        assertThat(service.getParticipantCards(7, snapshot)).containsExactlyElementsOf(snapshot);
    }

    @Test
    void rejectsSnapshotThatIsNotFifteenCards() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);

        assertThatThrownBy(() -> service.getParticipantCards(7, Collections.nCopies(14, 9L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 15 cards");
        assertThatThrownBy(() -> service.getParticipantCards(7, Collections.nCopies(16, 9L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 15 cards");
    }

    @Test
    void rejectsSnapshotThatNamesAMagicThatDoesNotExist() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        List<Long> snapshot = List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L,
                1L, 1L, 1L, 1L, 1L, 404L);
        when(repository.getMagics(List.of(1L, 404L)))
                .thenReturn(List.of(new CardsDto(1, "one", 1)));

        assertThatThrownBy(() -> service.getParticipantCards(7, snapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown magic");
    }

    // A lobby that predates the snapshot field sends no deck, and those sessions must keep
    // starting from the deck the user has selected.
    @Test
    void fallsBackToSelectedDeckWhenSnapshotIsMissing() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        when(repository.getSelectedDeck(7)).thenReturn(List.of(new CardsDto(34, "leafair", 2)));

        assertThat(service.getParticipantCards(7, null)).containsExactly(34L, 34L);
        verify(repository).getSelectedDeck(7);
        verifyNoMoreInteractions(repository);
    }
}
