package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.dto.input.InputResultCode;
import com.wordonline.server.game.dto.input.MagicUseRequestDto;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mana_cost and range are read under the magic's own name, which is what game_objects.name becomes.
 * They used to be read under the cast type name, so every Shoot magic shared one price and one
 * range; the numbers a magic actually gets are now its own.
 */
class MagicInputHandlerParameterKeyTest {

    private static final long LEAFAIR_ID = 34L;
    private static final String LEAFAIR = "leafair";

    private final DatabaseMagicParser magicParser = mock(DatabaseMagicParser.class);
    private final MagicInputHandler handler = new MagicInputHandler(magicParser);
    private final PlayerData playerData = new PlayerData(mock(ManaCharger.class));
    private final Parameters parameters = mock(Parameters.class);

    @Test
    void chargesTheManaCostAndClampsToTheRangeStoredUnderTheMagicName() {
        Magic magic = mock(Magic.class);
        magic.id = LEAFAIR_ID;
        magic.name = LEAFAIR;
        when(magicParser.parseMagic(1L, LEAFAIR_ID)).thenReturn(magic);
        when(parameters.getValue(LEAFAIR, "mana_cost")).thenReturn(20.0);
        when(parameters.getValue(LEAFAIR, "range")).thenReturn(18.0);

        playerData.mana = 50;
        playerData.addCard(LEAFAIR_ID);

        InputResponseDto response = handler.handleInput(
                gameContext(), 1L, new MagicUseRequestDto("useMagic", LEAFAIR_ID, 7, new Vector3(9f, 0f, 5f)));

        assertThat(response.resultCode()).isEqualTo(InputResultCode.SUCCESS);
        assertThat(playerData.mana).isEqualTo(30);
        assertThat(playerData.cards).isEmpty();
        verify(parameters).getValue(LEAFAIR, "mana_cost");
        verify(parameters).getValue(LEAFAIR, "range");
    }

    private GameContext gameContext() {
        GameObject caster = mock(GameObject.class);
        when(caster.getPosition()).thenReturn(new Vector3(1f, 0f, 1f));

        GameContext gameContext = mock(GameContext.class, RETURNS_DEEP_STUBS);
        when(gameContext.getSessionObject().getUserSide(1L)).thenReturn(Master.LeftPlayer);
        when(gameContext.getGameSessionData().getPlayerData(any())).thenReturn(playerData);
        when(gameContext.findPlayerGameObject(any(Master.class))).thenReturn(Optional.of(caster));
        when(gameContext.getParameters()).thenReturn(parameters);
        return gameContext;
    }
}
