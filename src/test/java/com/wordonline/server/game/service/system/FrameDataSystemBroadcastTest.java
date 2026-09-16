package com.wordonline.server.game.service.system;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.service.CardDeck;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameTimer;
import com.wordonline.server.game.service.ManaCharger;
import com.wordonline.server.websocket.SpectatorSubscriptionRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SimpMessagingTemplate serializes the payload on the calling thread, which is the game loop
 * thread, so a broadcast nobody subscribed to costs a JSON encode per frame before the broker
 * drops it. These pin the two halves of the fix: the payload is not built, and it is not sent.
 */
class FrameDataSystemBroadcastTest {

    private static final String SESSION_ID = "session-1";
    private static final String BROADCAST_DESTINATION = "/game/session-1/frameInfos/0";
    private static final long LEFT_USER_ID = 11L;
    private static final long RIGHT_USER_ID = 22L;

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SpectatorSubscriptionRegistry registry = mock(SpectatorSubscriptionRegistry.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final FrameDataSystem frameDataSystem = new FrameDataSystem();

    private SessionObject sessionObject;

    @BeforeEach
    void setUp() {
        sessionObject = new SessionObject(SESSION_ID, LEFT_USER_ID, RIGHT_USER_ID, template, List.of(), List.of());
        sessionObject.setSpectatorSubscriptionRegistry(registry);

        GameSessionData gameSessionData = new GameSessionData(playerData(), playerData());
        gameSessionData.initCardDeck(mock(CardDeck.class), mock(CardDeck.class));

        when(gameContext.getSessionObject()).thenReturn(sessionObject);
        when(gameContext.getGameSessionData()).thenReturn(gameSessionData);
        when(gameContext.getObjectsInfoDto()).thenReturn(new ObjectsInfoDto());
        when(gameContext.getGameTimer()).thenReturn(mock(GameTimer.class));
        when(gameContext.drainEvents()).thenReturn(List.of());
        when(gameContext.getFrameNum()).thenReturn(3);
    }

    @Test
    void buildsNoSpectatorPayloadWhenNobodyIsSubscribed() {
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(false);

        frameDataSystem.earlyUpdate(gameContext);
        frameDataSystem.lateUpdate(gameContext);

        assertThat(frameDataSystem.getBroadcastFrameInfoDto()).isNull();
        verify(template, never()).convertAndSend(eq(BROADCAST_DESTINATION), any(Object.class));
    }

    @Test
    void stillSendsBothPlayersTheirFramesWithoutSpectators() {
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(false);

        frameDataSystem.earlyUpdate(gameContext);
        frameDataSystem.lateUpdate(gameContext);

        verify(template).convertAndSend("/game/session-1/frameInfos/11",
                (Object) frameDataSystem.getLeftFrameInfoDto());
        verify(template).convertAndSend("/game/session-1/frameInfos/22",
                (Object) frameDataSystem.getRightFrameInfoDto());
    }

    @Test
    void buildsAndSendsTheSpectatorPayloadWhileSomeoneIsSubscribed() {
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(true);

        frameDataSystem.earlyUpdate(gameContext);
        frameDataSystem.lateUpdate(gameContext);

        FrameInfoDto broadcast = frameDataSystem.getBroadcastFrameInfoDto();
        assertThat(broadcast).isNotNull();
        assertThat(broadcast.getUpdatedMana()).isZero();
        verify(template).convertAndSend(BROADCAST_DESTINATION, (Object) broadcast);
    }

    @Test
    void picksUpASpectatorOnTheNextFrameAndDropsItWhenTheySubscribeAndLeave() {
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(false);
        frameDataSystem.earlyUpdate(gameContext);
        assertThat(frameDataSystem.getBroadcastFrameInfoDto()).isNull();

        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(true);
        frameDataSystem.earlyUpdate(gameContext);
        assertThat(frameDataSystem.getBroadcastFrameInfoDto()).isNotNull();

        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(false);
        frameDataSystem.earlyUpdate(gameContext);
        assertThat(frameDataSystem.getBroadcastFrameInfoDto()).isNull();
    }

    @Test
    void sendsNothingToTheBroadcastDestinationWhenTheSessionHasNoRegistryWired() {
        sessionObject.setSpectatorSubscriptionRegistry(null);

        frameDataSystem.earlyUpdate(gameContext);
        frameDataSystem.lateUpdate(gameContext);

        assertThat(frameDataSystem.getBroadcastFrameInfoDto()).isNull();
        verify(template, never()).convertAndSend(eq(BROADCAST_DESTINATION), any(Object.class));
    }

    private PlayerData playerData() {
        return new PlayerData(mock(ManaCharger.class));
    }
}
