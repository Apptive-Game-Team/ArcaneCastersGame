package com.wordonline.server.game.service.system;

import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component
public class GameActionSystem implements GameSystem {

    @Override
    public void update(GameContext gameContext) {
        gameContext.drainActions();
    }
}
