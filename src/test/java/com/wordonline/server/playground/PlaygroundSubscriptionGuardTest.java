package com.wordonline.server.playground;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.session.service.SessionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaygroundSubscriptionGuardTest {
    private final SessionService sessions = mock(SessionService.class);
    private final PlaygroundSubscriptionGuard guard = new PlaygroundSubscriptionGuard(sessions);
    private final String id = "playground-test";

    @Test void onlyLiveOwnerAdministratorCanSubscribeToTheirDestination() {
        var session = mock(SessionObject.class);
        var loop = mock(PlaygroundLoop.class);
        when(sessions.getSessionObject(id)).thenReturn(session);
        when(session.getGameLoop()).thenReturn(loop);
        when(session.getLeftUserId()).thenReturn(7L);
        when(loop.acceptsCommands()).thenReturn(true);
        assertThatCode(() -> subscribe("/game/" + id + "/frameInfos/7", 7, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> subscribe("/game/" + id + "/frameInfos/7", 8, true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscribe("/game/" + id + "/frameInfos/0", 7, true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscribe("/game/" + id + "/frameInfos/7", 7, false)).isInstanceOf(AccessDeniedException.class);
        when(loop.acceptsCommands()).thenReturn(false);
        assertThatThrownBy(() -> subscribe("/game/" + id + "/frameInfos/7", 7, true)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void wildcardAndMissingSessionsAreRejectedButOrdinaryDestinationsPass() {
        assertThatThrownBy(() -> subscribe("/game/*/frameInfos/0", 7, true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscribe("/game/" + id + "/frameInfos/7", 7, true)).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> subscribe("/game/normal/frameInfos/7", 7, false)).doesNotThrowAnyException();
    }
    private void subscribe(String destination, long userId, boolean admin) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setUser(new PrincipalDetails(userId, admin
                ? List.of(new SimpleGrantedAuthority("WORDONLINE_ADMIN")) : List.of()));
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        guard.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
    }
}
