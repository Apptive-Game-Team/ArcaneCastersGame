package com.wordonline.server.session.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.game.domain.map.GameMapSelector;
import com.wordonline.server.game.repository.MagicRepository;
import com.wordonline.server.session.dto.SessionDto;

/** Owned magics are read once at session creation, for human users only. */
class SessionObjectFactoryOwnedMagicsTest {

    private final MagicRepository magicRepository = mock(MagicRepository.class);
    private final DeckService deckService = mock(DeckService.class);
    private final GameMapSelector selector = mock(GameMapSelector.class);
    private final SessionObjectFactory factory =
            new SessionObjectFactory(mock(SimpMessagingTemplate.class), deckService, selector, magicRepository);

    @Test
    void humanUsersAreLoadedOnceAndBotsAreSkipped() {
        when(selector.choose(SessionType.Practice, null)).thenReturn(GameMap.GRASSLAND);
        when(deckService.getParticipantCards(anyLong())).thenReturn(List.of());
        when(magicRepository.findUserMagicIds(7L)).thenReturn(Set.of(1L, 2L));

        SessionObject session = factory.createSessionObject(
                new SessionDto("s-1", 7L, -1L, SessionType.Practice, null));

        assertThat(session.getOwnedMagicIds(7L)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(session.getOwnedMagicIds(-1L)).isEmpty();
        verify(magicRepository).findUserMagicIds(7L);
        verify(magicRepository, never()).findUserMagicIds(-1L);
    }
}
