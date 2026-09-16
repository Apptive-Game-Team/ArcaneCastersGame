package com.wordonline.server.game.service.system;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.dto.frame.SnapshotResponseDto;
import com.wordonline.server.game.dto.sync.SyncInfoDto;
import com.wordonline.server.game.service.CardDeck;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.GameTimer;
import com.wordonline.server.game.service.ManaCharger;
import com.wordonline.server.game.service.WordOnlineLoop;
import com.wordonline.server.websocket.SpectatorSubscriptionRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The spectator sync frame reuses the left player's snapshot as the canonical state, and that
 * snapshot carries myCards. Every spectator was receiving the left player's hand once every ten
 * frames; only the hand is removed, everything a player receives stays as it was.
 */
class SyncFrameDataSystemBroadcastTest {

    private static final String SESSION_ID = "session-1";
    private static final String BROADCAST_DESTINATION = "/game/session-1/frameInfos/0";
    private static final long LEFT_USER_ID = 11L;
    private static final long RIGHT_USER_ID = 22L;
    private static final List<Long> LEFT_HAND = List.of(2L, 1L);
    private static final List<Long> RIGHT_HAND = List.of(4L);

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SpectatorSubscriptionRegistry registry = mock(SpectatorSubscriptionRegistry.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final WordOnlineLoop gameLoop = mock(WordOnlineLoop.class);
    private final SyncFrameDataSystem syncFrameDataSystem = new SyncFrameDataSystem();

    @BeforeEach
    void setUp() {
        SessionObject sessionObject = new SessionObject(SESSION_ID, LEFT_USER_ID, RIGHT_USER_ID, template,
                List.of(), List.of());
        sessionObject.setSpectatorSubscriptionRegistry(registry);
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(true);

        GameSessionData gameSessionData = new GameSessionData(playerData(), playerData());
        gameSessionData.initCardDeck(mock(CardDeck.class), mock(CardDeck.class));

        when(gameContext.getSessionObject()).thenReturn(sessionObject);
        when(gameContext.getGameSessionData()).thenReturn(gameSessionData);
        when(gameContext.getObjectsInfoDto()).thenReturn(new ObjectsInfoDto());
        when(gameContext.getGameTimer()).thenReturn(mock(GameTimer.class));
        when(gameContext.drainEvents()).thenReturn(List.of());
        when(gameContext.getFrameNum()).thenReturn(GameLoop.SYNC_FRAME_INTERVAL);
        when(gameContext.getGameLoop()).thenReturn(gameLoop);

        when(gameLoop.getLastSnapshot(LEFT_USER_ID)).thenReturn(new SnapshotResponseDto(
                GameLoop.SYNC_FRAME_INTERVAL, List.of(), LEFT_HAND));
        when(gameLoop.getLastSnapshot(RIGHT_USER_ID)).thenReturn(new SnapshotResponseDto(
                GameLoop.SYNC_FRAME_INTERVAL, List.of(), RIGHT_HAND));
    }

    @Test
    void sendsSpectatorsASyncFrameWithoutAnyPlayerHand() {
        syncFrameDataSystem.earlyUpdate(gameContext);
        syncFrameDataSystem.lateUpdate(gameContext);

        SyncInfoDto broadcast = captureSyncSentTo(BROADCAST_DESTINATION);

        assertThat(broadcast.getSnapshotResponseDto().myCards()).isEmpty();
        assertThat(broadcast.getUpdatedMana()).isZero();
        assertThat(broadcast.getSnapshotResponseDto().frame()).isEqualTo(GameLoop.SYNC_FRAME_INTERVAL);
    }

    @Test
    void keepsSendingEachPlayerTheirOwnHand() {
        syncFrameDataSystem.earlyUpdate(gameContext);
        syncFrameDataSystem.lateUpdate(gameContext);

        assertThat(captureSyncSentTo("/game/session-1/frameInfos/11").getSnapshotResponseDto().myCards())
                .isEqualTo(LEFT_HAND);
        assertThat(captureSyncSentTo("/game/session-1/frameInfos/22").getSnapshotResponseDto().myCards())
                .isEqualTo(RIGHT_HAND);
    }

    @Test
    void sendsNoSpectatorSyncFrameWhenNobodyIsSubscribed() {
        when(registry.hasSubscribers(BROADCAST_DESTINATION)).thenReturn(false);

        syncFrameDataSystem.earlyUpdate(gameContext);
        syncFrameDataSystem.lateUpdate(gameContext);

        verify(template, never()).convertAndSend(eq(BROADCAST_DESTINATION), any(Object.class));
    }

    private SyncInfoDto captureSyncSentTo(String destination) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq(destination), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(SyncInfoDto.class);
        return (SyncInfoDto) payload.getValue();
    }

    private PlayerData playerData() {
        return new PlayerData(mock(ManaCharger.class));
    }
}
