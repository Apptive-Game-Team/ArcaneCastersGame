package com.wordonline.server.statistic.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.statistic.service.StatisticService;
import com.wordonline.server.statistic.util.PjpUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class StatisticAspect {

    private final StatisticService statisticService;

    /**
     * A human cast. The user id is an argument, so it is read straight off the join point.
     */
    @AfterReturning(
            value = "execution(com.wordonline.server.game.dto.input.InputResponseDto com.wordonline.server.game.service.MagicInputHandler.handleInput(..))",
            returning = "response"
    )
    public void afterHandleInput(JoinPoint joinPoint, InputResponseDto response) {
        if (!response.valid() || response.magicId() == -1) {
            return;
        }

        GameContext gameContext = PjpUtils.findArg(joinPoint.getArgs(), GameContext.class);
        Long userId = PjpUtils.findArg(joinPoint.getArgs(), Long.class);

        statisticService.saveMagic(gameContext, userId, response.magicId());
    }

    /**
     * A bot cast. Bots enter through their own method and carry a {@link Master} instead of a user
     * id, so the id is resolved back from the session. Without this a bot match ends with its deck
     * recorded in statistic_game_decks but not one row in statistic_game_magics, and every match
     * against a bot undercounts how often each magic was cast.
     *
     * <p>{@code handleBotMagicInput} is deliberately left out: a PvE boss casting is not a player
     * using a magic, and it has no user id to attribute the cast to.
     */
    @AfterReturning(
            value = "execution(com.wordonline.server.game.dto.input.InputResponseDto com.wordonline.server.game.service.MagicInputHandler.handleBotPlayerInput(..))",
            returning = "response"
    )
    public void afterHandleBotPlayerInput(JoinPoint joinPoint, InputResponseDto response) {
        if (!response.valid() || response.magicId() == -1) {
            return;
        }

        GameContext gameContext = PjpUtils.findArg(joinPoint.getArgs(), GameContext.class);
        Master master = PjpUtils.findArg(joinPoint.getArgs(), Master.class);

        Long userId = gameContext.getSessionObject().getUserId(master);
        if (userId == null) {
            log.warn("[Statistic] no user id for side {}; the cast of magic {} is not recorded",
                    master, response.magicId());
            return;
        }

        statisticService.saveMagic(gameContext, userId, response.magicId());
    }
}
