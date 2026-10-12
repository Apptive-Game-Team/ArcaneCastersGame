package com.wordonline.server.playground;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.repository.MagicRepository;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import com.wordonline.server.session.service.SessionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaygroundServiceTest {
    private final SessionService sessions = mock(SessionService.class);
    private final DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
    private final PlaygroundService service = new PlaygroundService(sessions, parser,
            mock(ServerUrlProvider.class), mock(ServerStatusService.class), mock(MagicRepository.class));

    private ApplicationContextRunner featureRunner() {
        return new ApplicationContextRunner().withInitializer(context -> {
            var factory = context.getBeanFactory();
            factory.registerSingleton("sessions", sessions);
            factory.registerSingleton("parser", parser);
            factory.registerSingleton("urls", mock(ServerUrlProvider.class));
            factory.registerSingleton("status", mock(ServerStatusService.class));
            factory.registerSingleton("catalog", mock(MagicRepository.class));
        }).withUserConfiguration(
                PlaygroundConfiguration.class, PlaygroundLoop.class, PlaygroundService.class,
                PlaygroundController.class, PlaygroundServerController.class, PropertyBinding.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlaygroundProperties.class)
    static class PropertyBinding {}

    @Test void featureIsEnabledWithoutSettings() {
        featureRunner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PlaygroundController.class)
                    .hasSingleBean(PlaygroundServerController.class).hasSingleBean(PlaygroundService.class);
            assertThat(context.containsBeanDefinition("playgroundLoop")).isTrue();
            assertThat(context.getBean(PlaygroundProperties.class).enabled()).isTrue();
        });
    }

    @Test void explicitFalseRemovesAllPlaygroundRoutesAndSimulationBeans() {
        featureRunner().withPropertyValues("playground.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(PlaygroundController.class)
                    .doesNotHaveBean(PlaygroundServerController.class).doesNotHaveBean(PlaygroundService.class)
                    .doesNotHaveBean(PlaygroundLoop.class);
            assertThat(context.containsBean("playgroundClock")).isFalse();
            assertThat(context.getBean(PlaygroundProperties.class).enabled()).isFalse();
        });
    }
    @Test void missingWrongOwnerOrdinaryAndExpiredSessionsCannotBeControlled() {
        assertThatThrownBy(() -> service.ownedLoop("missing", 7)).hasMessageContaining("404");
        var session = mock(SessionObject.class);
        var loop = mock(PlaygroundLoop.class);
        when(sessions.getSessionObject("playground-test")).thenReturn(session);
        when(session.getGameLoop()).thenReturn(loop);
        when(session.getLeftUserId()).thenReturn(7L);
        assertThatThrownBy(() -> service.ownedLoop("playground-test", 8)).hasMessageContaining("403");
        when(loop.isExpired()).thenReturn(true);
        assertThatThrownBy(() -> service.ownedLoop("playground-test", 7)).hasMessageContaining("410");
        when(session.getGameLoop()).thenReturn(mock(com.wordonline.server.game.service.WordOnlineLoop.class));
        assertThatThrownBy(() -> service.ownedLoop("playground-test", 7)).hasMessageContaining("404");
    }
    @Test void castsRealMagicForEitherSideWithoutHandOwnershipOrManaChecks() {
        var loop = mock(PlaygroundLoop.class);
        var context = mock(GameContext.class);
        var magic = mock(Magic.class);
        when(parser.parseMagicForBot(42)).thenReturn(magic);
        when(loop.getGameContext()).thenReturn(context);
        when(loop.reply(anyBoolean(), anyString())).thenAnswer(call ->
                new PlaygroundReply(call.getArgument(0), call.getArgument(1), false, false));
        var target = new Vector3(9, 0, 5);
        assertThat(service.cast(loop, 42, Master.RightPlayer, target).success()).isTrue();
        verify(magic).run(eq(context), eq(Master.RightPlayer), eq(target));
        assertThat(service.cast(loop, 42, Master.LeftPlayer, new Vector3(Float.NaN, 0, 5)).success()).isFalse();
        assertThat(service.cast(loop, 42, Master.LeftPlayer, new Vector3(19, 0, 5)).success()).isFalse();
        assertThat(service.cast(loop, 99, Master.LeftPlayer, target).success()).isFalse();
        verify(magic, times(1)).run(any(), any(), any());
    }
}
