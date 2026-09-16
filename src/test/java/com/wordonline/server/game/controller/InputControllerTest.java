package com.wordonline.server.game.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.PingChecker;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.dto.input.InputResultCode;
import com.wordonline.server.game.dto.input.MagicUseRequestDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.MagicInputHandler;
import com.wordonline.server.service.LocalizationService;
import com.wordonline.server.session.service.SessionService;

class InputControllerTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final LocalizationService localizationService = mock(LocalizationService.class);
    private final SessionObject sessionObject = mock(SessionObject.class);
    private final PingChecker pingChecker = mock(PingChecker.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final MagicInputHandler magicInputHandler = mock(MagicInputHandler.class);
    private final InputController controller = new InputController();

    InputControllerTest() {
        ReflectionTestUtils.setField(controller, "sessionService", sessionService);
        ReflectionTestUtils.setField(controller, "localizationService", localizationService);
        when(sessionService.getSessionObject("room-5")).thenReturn(sessionObject);
    }

    private static InputRequestDto pingRequest() {
        InputRequestDto dto = new InputRequestDto();
        dto.setType("ping");
        return dto;
    }

    private static PrincipalDetails principal(long uid) {
        PrincipalDetails principalDetails = mock(PrincipalDetails.class);
        when(principalDetails.getUid()).thenReturn(uid);
        return principalDetails;
    }

    @Test
    void rejectsInputFromUserWhoIsNotInTheSession() {
        when(sessionObject.getUserSide(77L)).thenReturn(null);

        assertThatThrownBy(() -> controller.handleInput("room-5", 77L, pingRequest(), principal(77L)))
                .isInstanceOf(AuthorizationDeniedException.class);

        verifyNoInteractions(pingChecker);
    }

    @Test
    void acceptsInputFromSessionParticipant() {
        when(sessionObject.getUserSide(7L)).thenReturn(Master.LeftPlayer);
        when(sessionObject.getPingChecker()).thenReturn(pingChecker);

        assertThatCode(() -> controller.handleInput("room-5", 7L, pingRequest(), principal(7L)))
                .doesNotThrowAnyException();

        verify(pingChecker).ping(7L);
    }

    // The controller runs on a STOMP inbound thread. Casting inline there is what the game action
    // queue exists to prevent, so the handler must not be touched until the loop drains the queue.
    @Test
    void queuesMagicUseInsteadOfCastingOnTheInboundThread() {
        when(sessionObject.getUserSide(7L)).thenReturn(Master.LeftPlayer);
        when(sessionObject.getGameContext()).thenReturn(gameContext);
        when(gameContext.getMagicInputHandler()).thenReturn(magicInputHandler);
        InputResponseDto response = new InputResponseDto(true, InputResultCode.SUCCESS, 3, 1, 9L);
        when(magicInputHandler.handleInput(eq(gameContext), eq(7L), any(MagicUseRequestDto.class)))
                .thenReturn(response);

        controller.handleInput("room-5", 7L, magicRequest(), principal(7L));

        verifyNoInteractions(magicInputHandler);

        drainQueuedAction("useMagic");

        verify(magicInputHandler).handleInput(eq(gameContext), eq(7L), any(MagicUseRequestDto.class));
        verify(sessionObject).sendFrameInfo(7L, response);
    }

    // Card selection mutates components on the live player object, so it belongs on the loop thread
    // for the same reason a cast does.
    @Test
    void queuesCardSelectionInsteadOfMutatingOnTheInboundThread() {
        when(sessionObject.getUserSide(7L)).thenReturn(Master.LeftPlayer);
        when(sessionObject.getGameContext()).thenReturn(gameContext);

        InputRequestDto request = new InputRequestDto();
        request.setType("selectCard");
        request.setMagicId(34L);

        controller.handleInput("room-5", 7L, request, principal(7L));

        verify(gameContext, never()).selectCard(anyLong(), anyLong());

        drainQueuedAction("selectCard");

        verify(gameContext).selectCard(7L, 34L);
    }

    private static InputRequestDto magicRequest() {
        InputRequestDto dto = new InputRequestDto();
        dto.setType("useMagic");
        dto.setMagicId(34L);
        dto.setPosition(Vector3.ZERO);
        dto.setId(1);
        return dto;
    }

    // Stands in for the loop thread: pulls the action the controller queued and runs it.
    private void drainQueuedAction(String expectedName) {
        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        verify(gameContext).submitAction(eq(expectedName), action.capture());
        action.getValue().run();
    }
}
