package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotAction {

    // Called on the bot executor thread, so the cast is queued instead of run here. The loop
    // thread executes it at the top of the next frame.
    public void useCard(SessionObject sessionObject, InputRequestDto inputRequestDto, Master botSide)
    {
        log.debug("[Bot {}] Executing action: {} magicId={} target={}",
                botSide, inputRequestDto.getType(), inputRequestDto.getMagicId(), inputRequestDto.getPosition());

        GameContext gameContext = sessionObject.getGameContext();
        gameContext.submitAction("botUseMagic", () ->
                gameContext.getMagicInputHandler().handleBotPlayerInput(gameContext, botSide, inputRequestDto));
    }
}
