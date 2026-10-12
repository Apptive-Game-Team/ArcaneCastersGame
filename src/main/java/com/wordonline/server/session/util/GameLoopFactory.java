package com.wordonline.server.session.util;

import com.wordonline.server.game.config.GameTickProperties;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.PveLoop;
import com.wordonline.server.game.service.WordOnlineLoop;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GameLoopFactory {
    private final ObjectProvider<WordOnlineLoop> wordOnlineLoopProvider;
    private final ObjectProvider<PveLoop> pveLoopProvider;
    private final ObjectProvider<com.wordonline.server.playground.PlaygroundLoop> playgroundLoopProvider;
    private final GameTickProperties gameTickProperties;

    public GameLoopFactory(@Qualifier("wordOnlineLoop") ObjectProvider<WordOnlineLoop> wordOnlineLoopProvider,
                           @Qualifier("pveLoop") ObjectProvider<PveLoop> pveLoopProvider,
                           ObjectProvider<com.wordonline.server.playground.PlaygroundLoop> playgroundLoopProvider,
                           GameTickProperties gameTickProperties) {
        this.wordOnlineLoopProvider = wordOnlineLoopProvider;
        this.pveLoopProvider = pveLoopProvider;
        this.playgroundLoopProvider = playgroundLoopProvider;
        this.gameTickProperties = gameTickProperties;
    }

    // The loop comes back at the configured tick rate; a session that asks for its own rate
    // overrides it on the context before the loop is initialized.
    public GameLoop create(SessionType sessionType) {
        GameLoop loop = createLoop(sessionType);
        loop.getGameContext().setTickRate(gameTickProperties.tickRate());
        return loop;
    }

    private GameLoop createLoop(SessionType sessionType) {
        if (sessionType == SessionType.Playground) return playgroundLoopProvider.getObject();
        if (sessionType == SessionType.PVE) {
            return pveLoopProvider.getObject();
        }
        return wordOnlineLoopProvider.getObject();
    }
}
