package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.pve.PveObjectiveDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.PveResultChecker;

/**
 * Sends the PVE win condition to both users on the first update, then again when the remaining
 * seconds or the remaining objectives change, and once every {@value #RESEND_SECONDS} seconds
 * even when nothing changed.
 *
 * <p>The resend is what lets a client that subscribed late, or reconnected, learn the objective.
 * The match loop starts before the client has subscribed to its frame topic, and a topic message
 * is not replayed, so a message sent once at the start can be lost. A match that is won by
 * destroying targets changes only when a target dies, so without the resend its objective line
 * would stay missing until the first kill.
 */
public class PveObjectiveSystem implements GameSystem {

    static final int RESEND_SECONDS = 2;
    private static final int RESEND_FRAMES = RESEND_SECONDS * GameLoop.FPS;

    private final PveResultChecker resultChecker;
    private PveObjectiveDto lastSent;
    private int lastSentFrame;

    public PveObjectiveSystem(PveResultChecker resultChecker) {
        this.resultChecker = resultChecker;
    }

    @Override
    public void update(GameContext gameContext) {
        int frameNum = gameContext.getFrameNum();
        PveObjectiveDto dto = build(frameNum);
        if (lastSent != null
                && lastSent.remainingSeconds() == dto.remainingSeconds()
                && lastSent.objectivesRemaining() == dto.objectivesRemaining()
                && frameNum - lastSentFrame < RESEND_FRAMES) {
            return;
        }
        lastSent = dto;
        lastSentFrame = frameNum;

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
