package com.wordonline.server.bot.service;

import com.wordonline.server.bot.config.BotAutoMatchProperties;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = BotGameSchedulerSchedulingTest.SchedulingConfig.class)
@TestPropertySource(properties = {
        "bot.auto-match.enabled=true",
        "bot.auto-match.target-games=1",
        "bot.auto-match.check-interval-ms=25",
        "bot.auto-match.max-sessions-per-sweep=5",
        "server.max-sessions=100",
        "server.external-port=7777",
        "server.domain=localhost",
        "server.protocol=http"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BotGameSchedulerSchedulingTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private BotPersonaService botPersonaService;

    @Autowired
    private ServerStatusService serverStatusService;

    @BeforeEach
    void setUp() {
        reset(sessionService, botPersonaService, serverStatusService);
    }

    @Test
    void scheduledTaskCreatesBotGameWhenServerIsIdle() {
        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(
                bot(-1, "Intro Bot A"),
                bot(-2, "Beginner Bot A")
        ));

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(sessionService, atLeastOnce()).createSession(any(SessionDto.class)));
    }

    private BotPersona bot(long id, String name) {
        return new BotPersona(
                id,
                name,
                BotTier.BEGINNER,
                250,
                8,
                0.25,
                true,
                false,
                BotTemperament.WARM
        );
    }

    @Configuration
    @EnableScheduling
    @EnableConfigurationProperties({
        BotAutoMatchProperties.class,
        ServerIdentityProperties.class,
        BotGameScheduler.SweepProperties.class
})
    static class SchedulingConfig {

        @Bean
        BotGameScheduler botGameScheduler(SessionService sessionService,
                                          BotPersonaService botPersonaService,
                                          ServerStatusService serverStatusService,
                                          BotAutoMatchProperties botAutoMatchProperties,
                                          ServerIdentityProperties serverIdentityProperties,
                                          BotGameScheduler.SweepProperties sweepProperties) {
            return new BotGameScheduler(sessionService, botPersonaService, serverStatusService,
                    botAutoMatchProperties, serverIdentityProperties, sweepProperties);
        }

        @Bean
        SessionService sessionService() {
            return mock(SessionService.class);
        }

        @Bean
        BotPersonaService botPersonaService() {
            return mock(BotPersonaService.class);
        }

        @Bean
        ServerStatusService serverStatusService() {
            return mock(ServerStatusService.class);
        }
    }
}
