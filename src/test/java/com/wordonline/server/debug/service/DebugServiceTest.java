package com.wordonline.server.debug.service;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.debug.dto.DebugGameRequestDto;
import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.repository.MagicRepository;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebugServiceTest {

    @Mock SessionService sessionService;
    @Mock DeckService deckService;
    @Mock DatabaseMagicParser magicParser;
    @Mock MagicRepository magicRepository;
    @Mock BotPersonaService botPersonaService;

    private DebugService debugService;

    @BeforeEach
    void setUp() {
        debugService = new DebugService(sessionService, deckService, magicParser, magicRepository, botPersonaService);
    }

    @Test
    void practiceUsesRandomEnabledBotAndPracticeSessionType() {
        when(botPersonaService.findRandomEnabled()).thenReturn(Optional.of(persona(-12)));

        debugService.enterPracticeSession(new DebugGameRequestDto(Master.LeftPlayer, 31, null));

        SessionDto session = captureCreatedSession();
        assertThat(session.uid1()).isEqualTo(31);
        assertThat(session.uid2()).isEqualTo(-12);
        assertThat(session.sessionType()).isEqualTo(SessionType.Practice);
        assertThat(session.scenarioId()).isNull();
    }

    @Test
    void practiceFailsWhenNoEnabledBotExists() {
        when(botPersonaService.findRandomEnabled()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debugService.enterPracticeSession(
                new DebugGameRequestDto(Master.LeftPlayer, 31, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No enabled bot persona");
    }

    @Test
    void pveKeepsScenarioAndPveSessionType() {
        debugService.enterPveSession(new DebugGameRequestDto(Master.LeftPlayer, 31, 9L));

        SessionDto session = captureCreatedSession();
        assertThat(session.uid1()).isEqualTo(31);
        assertThat(session.uid2()).isEqualTo(-1);
        assertThat(session.sessionType()).isEqualTo(SessionType.PVE);
        assertThat(session.scenarioId()).isEqualTo(9L);
    }

    private SessionDto captureCreatedSession() {
        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService).createSession(captor.capture());
        return captor.getValue();
    }

    private BotPersona persona(long userId) {
        return new BotPersona(userId, "Random Bot", BotTier.BEGINNER, 250, 8, 0.25, true, false,
                BotTemperament.WARM);
    }
}
