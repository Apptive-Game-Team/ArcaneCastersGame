package com.wordonline.server.game.service.system;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.wordonline.server.game.domain.bot.BotAgent;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.dto.Master;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.service.GameContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Scope("prototype")
public class BotAgentSystem implements GameSystem {

    private final ExecutorService botExecutorService;
    private final AtomicBoolean leftBotProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rightBotProcessing = new AtomicBoolean(false);
    private final AtomicInteger frameCounter = new AtomicInteger(0);

    @Override
    public void update(GameContext gameContext) {
        int currentFrame = frameCounter.incrementAndGet();

        if (gameContext.getGameLoop() == null) {
            log.warn("[BotSystem] GameLoop is null, skipping bot update");
            return;
        }

        var wordOnlineLoop = gameContext.getGameLoop();
        log.trace("[BotSystem] Triggering bot tick at frame {}", currentFrame);

        submitBotIfNeeded(wordOnlineLoop.getLeftBotAgent(), currentFrame, leftBotProcessing,
                gameContext, Master.LeftPlayer, "Left");

        submitBotIfNeeded(wordOnlineLoop.getRightBotAgent(), currentFrame, rightBotProcessing,
                gameContext, Master.RightPlayer, "Right");

        // Emotes are decided here, on the loop thread, rather than inside the executor tick: the
        // director reads the health of both sides live and sends through the session directly, and
        // it is cheap enough to ask every frame.
        long nowMillis = System.currentTimeMillis();
        updateEmote(wordOnlineLoop.getLeftBotAgent(), gameContext, nowMillis);
        updateEmote(wordOnlineLoop.getRightBotAgent(), gameContext, nowMillis);
    }

    private void updateEmote(BotAgent botAgent, GameContext gameContext, long nowMillis) {
        if (botAgent == null) {
            return;
        }
        botAgent.updateEmote(gameContext.getGameSessionData(), nowMillis);
    }

    private void submitBotIfNeeded(BotAgent botAgent,
                                   int currentFrame,
                                   AtomicBoolean processing,
                                   GameContext gameContext,
                                   Master botSide,
                                   String label) {
        if (botAgent == null || !botAgent.shouldProcess(currentFrame)) {
            return;
        }

        if (processing.compareAndSet(false, true)) {
            // Taken here, on the loop thread, mid-frame but with nothing else mutating game state.
            // The executor gets an immutable copy and never reaches back into the live objects.
            BotEye botEye = BotEye.observe(gameContext.getGameSessionData(), botSide);
            log.debug("[BotSystem] Submitting {} Bot task", label);
            botExecutorService.submit(() -> {
                try {
                    botAgent.onTick(botEye);
                } catch (Exception e) {
                    log.error("[BotSystem] {} bot agent execution error", label, e);
                } finally {
                    processing.set(false);
                }
            });
        } else {
            log.trace("[BotSystem] {} bot is still processing, skipping this tick", label);
        }
    }
}
