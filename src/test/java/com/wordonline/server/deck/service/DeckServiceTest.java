package com.wordonline.server.deck.service;

import com.wordonline.server.deck.dto.CardsDto;
import com.wordonline.server.deck.repository.DeckRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckServiceTest {

    @Test
    void loadsBotDeckFromNegativeUserSelectedDeck() {
        DeckRepository repository = mock(DeckRepository.class);
        DeckService service = new DeckService(repository);
        when(repository.getSelectedDeck(-7)).thenReturn(List.of(new CardsDto(34, "leafair", 2)));

        assertThat(service.getParticipantCards(-7)).containsExactly(34L, 34L);
        verify(repository).getSelectedDeck(-7);
    }
}
