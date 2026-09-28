package com.wordonline.server.game.controller;

import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.session.service.SessionService;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.Emote;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.EmoteFrameDto;
import com.wordonline.server.game.dto.input.EmoteRequestDto;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.game.dto.input.InputResponseDto;
import com.wordonline.server.game.dto.input.MagicUseRequestDto;
import com.wordonline.server.game.domain.bot.BotAgent;
import com.wordonline.server.game.domain.bot.BotSideUtil;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.WordOnlineLoop;
import com.wordonline.server.service.LocalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@MessageMapping("/game/input")
public class InputController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private LocalizationService localizationService;

    @MessageMapping("{sessionId}/{userId}")
    public void handleInput(
            @DestinationVariable String sessionId,
            @DestinationVariable long userId,
            @Payload InputRequestDto inputRequestDto,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        if (userId != principalDetails.getUid()) {
            throw new AuthorizationDeniedException(localizationService.getMessage("error.authorization.denied"));
        }

        SessionObject sessionObject = sessionService.getSessionObject(sessionId);

        if (sessionObject == null) return;

        if (sessionObject.getUserSide(userId) == null) {
            throw new AuthorizationDeniedException(localizationService.getMessage("error.authorization.denied"));
        }

        log.trace("input arrived {}", inputRequestDto.getType());

        // This runs on a STOMP inbound thread. Anything that touches game state is queued for the
        // loop thread; the request payload is converted here so a malformed one is rejected with an
        // exception on this thread rather than logged out of a queued action.
        GameContext gameContext = sessionObject.getGameContext();

        switch (inputRequestDto.getType()) {
            case "useMagic" -> {
                log.trace("useMagic arrived {}", userId);
                MagicUseRequestDto magicUse = inputRequestDto.toMagicUse();
                gameContext.submitAction("useMagic", () -> {
                    InputResponseDto responseDto = gameContext.getMagicInputHandler()
                            .handleInput(gameContext, userId, magicUse);
                    sessionObject.sendFrameInfo(userId, responseDto);
                });
            }
            case "ping" -> {
                log.trace("ping arrived {}", userId);
                sessionObject.getPingChecker().ping(userId);
            }
            case "emote" -> {
                log.trace("emote arrived {}", userId);
                EmoteRequestDto emoteRequest = inputRequestDto.toEmote();
                Emote emote;
                try {
                    emote = Emote.valueOf(emoteRequest.emote());
                } catch (IllegalArgumentException | NullPointerException e) {
                    log.warn("Unknown emote from user {}: {}", userId, emoteRequest.emote());
                    return;
                }
                Master side = sessionObject.getUserSide(userId);
                if (sessionObject.tryConsumeEmoteCooldown(side)) {
                    sessionObject.sendEmote(new EmoteFrameDto(side, emote));
                    notifyOpposingBot(gameContext, side, emote);
                }
            }
            case "selectCard" -> {
                log.trace("selectCard arrived {}", userId);
                long magicId = inputRequestDto.toCardAim().magicId();
                gameContext.submitAction("selectCard", () -> gameContext.selectCard(userId, magicId));
            }
            case "unselectCard" -> {
                log.trace("unselectCard arrived {}", userId);
                long magicId = inputRequestDto.toCardAim().magicId();
                gameContext.submitAction("unselectCard", () -> gameContext.unselectCard(userId, magicId));
            }
            case null, default -> log.warn("Unknown input type: {}", inputRequestDto.getType());
        }

    }

    /**
     * Lets a bot on the other side know it was emoted at, so it can answer.
     *
     * <p>The emote itself changes no game state and still goes out from this inbound thread. Only
     * the bot's knowledge of it crosses to the loop thread, and it crosses the way every other
     * input does - queued as an action - because the director that decides the answer runs there.
     * A session with a human on the other side queues nothing.
     */
    private void notifyOpposingBot(GameContext gameContext, Master senderSide, Emote emote) {
        WordOnlineLoop gameLoop = gameContext == null ? null : gameContext.getGameLoop();
        if (gameLoop == null) {
            return;
        }
        Master botSide = BotSideUtil.getEnemySide(senderSide);
        if (gameLoop.getBotAgent(botSide) == null) {
            return;
        }

        gameContext.submitAction("botEmoteReply", () -> {
            // Read again on the loop thread: the bot may have been swapped out for a reconnecting
            // player between the queue and the frame that drains it.
            BotAgent botAgent = gameLoop.getBotAgent(botSide);
            if (botAgent != null) {
                botAgent.onOpponentEmote(emote);
            }
        });
    }
}
