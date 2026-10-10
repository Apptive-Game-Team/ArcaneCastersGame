package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.dto.input.InputResultCode;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.MagicInputHandler;

class PVEBossMobCooldownTest {

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void aCastMagicIsReadyAgainAfterTwoSecondsAtAnyTickRate(int tickRate) {
        GameObject boss = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        MagicInputHandler magicInputHandler = mock(MagicInputHandler.class);
        when(boss.getGameContext()).thenReturn(gameContext);
        when(boss.getMaster()).thenReturn(Master.RightPlayer);
        when(boss.getPosition()).thenReturn(Vector3.ZERO);
        when(target.getPosition()).thenReturn(new Vector3(3f, 0f, 0f));
        when(gameContext.getDeltaTime()).thenReturn(1f / tickRate);
        when(gameContext.getMagicInputHandler()).thenReturn(magicInputHandler);
        when(magicInputHandler.handleBotMagicInput(any(), any(), any(), any(), any()))
                .thenReturn(new InputResponseDto(true, InputResultCode.SUCCESS, 0, 0, 1L));
        Magic magic = mock(Magic.class);
        magic.id = 1L;
        PVEBossMob mob = new PVEBossMob(boss, 100, 1f, 0, 1f, 5f, List.of(magic));

        assertThat(mob.tryCastMagic(magic, target)).isTrue();
        int frames = 0;
        boolean cast;
        do {
            mob.update();
            frames++;
            cast = mob.tryCastMagic(magic, target);
        } while (!cast && frames < 10 * tickRate);

        assertThat(frames / (double) tickRate).isEqualTo(2.0);
    }
}
