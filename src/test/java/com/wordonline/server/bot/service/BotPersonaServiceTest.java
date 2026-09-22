package com.wordonline.server.bot.service;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.dto.BotPersonaRequestDto;
import com.wordonline.server.bot.repository.BotPersonaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotPersonaServiceTest {

    private final BotPersonaRepository repository = mock(BotPersonaRepository.class);
    private final BotPersonaService service = new BotPersonaService(repository);

    @Test
    void resolvesBotPersonaUsingParticipantUserIdDirectly() {
        BotPersona persona = persona(-7);
        when(repository.findByUserId(-7)).thenReturn(Optional.of(persona));

        assertThat(service.findByParticipantIdOrDefault(-7)).isEqualTo(persona);
        verify(repository).findByUserId(-7);
    }

    @Test
    void usesDefaultPersonaForHumanParticipant() {
        assertThat(service.findByParticipantIdOrDefault(7)).isEqualTo(BotPersona.DEFAULT);
    }

    @Test
    void rejectsPositiveUserIdOnCreate() {
        assertThatThrownBy(() -> service.create(request(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsChangingUserIdOnUpdate() {
        assertThatThrownBy(() -> service.update(-7, request(-8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be changed");
    }

    @Test
    void reportsMissingPersonaOnDelete() {
        when(repository.delete(-7)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(-7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void selectsTheOnlyEnabledPersona() {
        BotPersona persona = persona(-7);
        when(repository.findAll()).thenReturn(List.of(persona));

        assertThat(service.findRandomEnabled()).contains(persona);
    }

    @Test
    void returnsEmptyWhenNoPersonaIsEnabled() {
        when(repository.findAll()).thenReturn(List.of(
                new BotPersona(-7, "Disabled", BotTier.BEGINNER, 250, 8, 0.25, false, false,
                        BotTemperament.WARM)
        ));

        assertThat(service.findRandomEnabled()).isEmpty();
    }

    private BotPersonaRequestDto request(long userId) {
        return new BotPersonaRequestDto(userId, "Bot", BotTier.BEGINNER, 250, 8, 0.25, true, false,
                BotTemperament.WARM);
    }

    private BotPersona persona(long userId) {
        return new BotPersona(userId, "Bot", BotTier.BEGINNER, 250, 8, 0.25, true, false,
                BotTemperament.WARM);
    }
}
