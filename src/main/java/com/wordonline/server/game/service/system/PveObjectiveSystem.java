package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.pve.PveObjectiveDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.PveResultChecker;

/**
 * Sends the PVE win condition to both users on the first update, then again only when the
 * remaining seconds or the remaining objectives change.
 */
public class PveObjectiveSystem implements GameSystem {

    private final PveResultChecker resultChecker;
    private PveObjectiveDto lastSent;

    public PveObjectiveSystem(PveResultChecker resultChecker) {
        this.resultChecker = resultChecker;
    }

    @Override
    public void update(GameContext gameContext) {
        PveObjectiveDto dto = build(gameContext.getFrameNum());
        if (lastSent != null
                && lastSent.remainingSeconds() == dto.remainingSeconds()
                && lastSent.objectivesRemaining() == dto.objectivesRemaining()) {
            return;
        }
        lastSent = dto;

        SessionObject sessionObject = gameContext.getSessionObject();
        sessionObject.sendFrameInfo(sessionObject.getLeftUserId(), dto);
        sessionObject.sendFrameInfo(sessionObject.getRightUserId(), dto);
    }

    private PveObjectiveDto build(int frameNum) {
        PveWinCondition winCondition = resultChecker.getWinCondition();
        if (winCondition == PveWinCondition.Survive) {
            int remainingFrames = Math.max(0, resultChecker.getSurviveFrameThreshold() - frameNum);
            int remainingSeconds = (remainingFrames + GameLoop.FPS - 1) / GameLoop.FPS;
            return new PveObjectiveDto(winCondition, resultChecker.getSurviveSeconds(), remainingSeconds, 0, 0);
        }
        return new PveObjectiveDto(winCondition, 0, 0,
                resultChecker.getObjectivesTotal(), resultChecker.countObjectivesRemaining());
    }
}
