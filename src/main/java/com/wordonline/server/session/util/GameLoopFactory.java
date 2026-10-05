package com.wordonline.server.session.util;

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

    public GameLoopFactory(@Qualifier("wordOnlineLoop") ObjectProvider<WordOnlineLoop> wordOnlineLoopProvider,
                           @Qualifier("pveLoop") ObjectProvider<PveLoop> pveLoopProvider,
                           ObjectProvider<com.wordonline.server.playground.PlaygroundLoop> playgroundLoopProvider) {
        this.wordOnlineLoopProvider = wordOnlineLoopProvider;
        this.pveLoopProvider = pveLoopProvider;
        this.playgroundLoopProvider = playgroundLoopProvider;
    }

    public GameLoop create(SessionType sessionType) {
        if (sessionType == SessionType.Playground) return playgroundLoopProvider.getObject();
        if (sessionType == SessionType.PVE) {
            return pveLoopProvider.getObject();
        }
        return wordOnlineLoopProvider.getObject();
    }
}
