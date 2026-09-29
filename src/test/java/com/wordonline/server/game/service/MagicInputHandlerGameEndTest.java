package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.dto.input.InputResultCode;
import com.wordonline.server.game.dto.input.MagicUseRequestDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MagicInputHandlerGameEndTest {

    private static final long MAGIC_ID = 34L;

    private final DatabaseMagicParser magicParser = mock(DatabaseMagicParser.class);
    private final MagicInputHandler handler = new MagicInputHandler(magicParser);
    private final PlayerData playerData = new PlayerData(mock(ManaCharger.class));

    @Test
    void rejectsPlayerCastAfterEndWithoutUsingCard() {
        GameContext gameContext = endedGameContext();

        InputResponseDto response = handler.handleInput(gameContext, 1L,
                new MagicUseRequestDto("useMagic", MAGIC_ID, 7, new Vector3(1f, 0f, 1f)));

        assertRejected(response, 7);
        assertThat(playerData.cards).containsExactly(MAGIC_ID);
        verifyNoInteractions(magicParser);
    }

    @Test
    void rejectsBotPlayerCastAfterEndWithoutUsingCard() {
        GameContext gameContext = endedGameContext();
        InputRequestDto request = new InputRequestDto();
        request.setMagicId(MAGIC_ID);
        request.setId(8);
        request.setPosition(new Vector3(1f, 0f, 1f));

        InputResponseDto response = handler.handleBotPlayerInput(gameContext, Master.LeftPlayer, request);

        assertRejected(response, 8);
        assertThat(playerData.cards).containsExactly(MAGIC_ID);
        verifyNoInteractions(magicParser);
    }

    @Test
    void rejectsBotMagicCastAfterEndWithoutSpendingMana() {
        GameContext gameContext = endedGameContext();
        Magic magic = mock(Magic.class);

        InputResponseDto response = handler.handleBotMagicInput(gameContext, Master.LeftPlayer,
                magic, new Vector3(1f, 0f, 1f), new Vector3(0f, 0f, 0f));

        assertRejected(response, -1);
        assertThat(playerData.mana).isEqualTo(50);
        verifyNoInteractions(magic);
    }

    private void assertRejected(InputResponseDto response, int requestId) {
        assertThat(response.valid()).isFalse();
        assertThat(response.resultCode()).isEqualTo(InputResultCode.FAIL_GAME_ENDED);
        assertThat(response.message()).isEqualTo("Game has ended.");
        assertThat(response.updatedMana()).isEqualTo(50);
        assertThat(response.id()).isEqualTo(requestId);
    }

    private GameContext endedGameContext() {
        playerData.mana = 50;
        playerData.addCard(MAGIC_ID);

        GameContext gameContext = mock(GameContext.class, RETURNS_DEEP_STUBS);
        when(gameContext.getSessionObject().getUserSide(1L)).thenReturn(Master.LeftPlayer);
        when(gameContext.getGameSessionData().getPlayerData(any())).thenReturn(playerData);
        when(gameContext.getResultChecker().checkResult()).thenReturn(true);
        return gameContext;
    }
}
