package com.wordonline.server.game.domain;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.WordOnlineLoop;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The destinations are cached rather than formatted per send, so these pin the addresses that
 * cache has to keep producing, including after a debug endpoint swaps a user out.
 */
class SessionObjectDestinationTest {

    private static final String SESSION_ID = "session-1";

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);

    @Test
    void sendsToTheDestinationOfEachPlayer() {
        SessionObject sessionObject = sessionObject(11L, 22L);

        sessionObject.sendFrameInfo(11L, "left");
        sessionObject.sendFrameInfo(22L, "right");

        verify(template).convertAndSend("/game/session-1/frameInfos/11", (Object) "left");
        verify(template).convertAndSend("/game/session-1/frameInfos/22", (Object) "right");
    }

    @Test
    void sendsToADestinationOfAUserThatIsNeitherPlayer() {
        SessionObject sessionObject = sessionObject(11L, 22L);

        sessionObject.sendFrameInfo(33L, "stranger");

        verify(template).convertAndSend("/game/session-1/frameInfos/33", (Object) "stranger");
    }

    @Test
    void followsTheUserIdWhenADebugEndpointSwapsAPlayerIn() {
        SessionObject sessionObject = sessionObject(11L, 22L);
        WordOnlineLoop gameLoop = mock(WordOnlineLoop.class);
        when(gameLoop.getGameContext()).thenReturn(mock(GameContext.class));
        sessionObject.setGameLoop(gameLoop);

        sessionObject.setLeftUser(44L, List.of());

        sessionObject.sendFrameInfo(44L, "left");

        verify(template).convertAndSend("/game/session-1/frameInfos/44", (Object) "left");
    }

    @Test
    void sendsNothingToBots() {
        SessionObject sessionObject = sessionObject(11L, -1L);

        sessionObject.sendFrameInfo(-1L, "bot");

        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
    }

    private SessionObject sessionObject(long leftUserId, long rightUserId) {
        return new SessionObject(SESSION_ID, leftUserId, rightUserId, template,
                List.<Long>of(), List.<Long>of());
    }
}
