package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PveResultChecker extends ResultChecker {
    private boolean cleared = false;
    private boolean failed = false;

    private List<String> objectiveInstallerIds = List.of();
    private PveScenarioInstaller.RuntimeState runtime;
    private final Set<Integer> seenObjectives = new HashSet<>();

    private PveWinCondition winCondition = PveWinCondition.DestroyObjectives;
    private int surviveSeconds = 0;

    public PveResultChecker(SessionObject sessionObject) {
        super(sessionObject);
    }

    // Objectives are resolved by installer id on every check, so an objective installed later by
    // an InstallObject action is picked up as soon as it exists; one that is never installed
    // resolves to no object id and blocks the win forever.
    public void setObjectiveInstallerIds(List<String> objectiveInstallerIds) {
        this.objectiveInstallerIds = objectiveInstallerIds == null ? List.of() : new ArrayList<>(objectiveInstallerIds);
        this.seenObjectives.clear();
    }

    public void setRuntime(PveScenarioInstaller.RuntimeState runtime) {
        this.runtime = runtime;
    }

    public void configureRules(PveScenarioRules rules) {
        PveScenarioRules effective = rules == null ? PveScenarioRules.defaultRules() : rules;
        this.winCondition = effective.winCondition();
        if (winCondition == PveWinCondition.Survive) {
            this.surviveSeconds = effective.surviveSeconds() == null ? 0 : effective.surviveSeconds();
        } else {
            this.surviveSeconds = 0;
        }
    }

    public void setCleared() {
        cleared = true;
        setLoser(Master.RightPlayer);
    }

    public void setFailed() {
        failed = true;
        setLoser(Master.LeftPlayer);
    }

    @Override
    public boolean checkResult() {
        if (!cleared && !failed) {
            // Lose condition: left character dies.
            if (getLoser() == Master.LeftPlayer) {
                setFailed();
            }
        }

        if (!cleared && !failed) {
            if (winCondition == PveWinCondition.Survive) {
                checkSurvive();
            } else if (areAllObjectivesTerminal()) {
                setCleared();
            }
        }

        return cleared || failed;
    }

    private void checkSurvive() {
        // The player only has to still be alive here, which the lose check above already
        // guarantees for this branch to be reached. Destroying every objective ends the match
        // early; a scenario with no objectives is cleared by the timer alone.
        GameContext gameContext = getSessionObject().getGameContext();
        if (gameContext.getFrameNum() >= getSurviveFrameThreshold(gameContext.getTickRate())
                || areAllObjectivesTerminal()) {
            setCleared();
        }
    }

    public PveWinCondition getWinCondition() {
        return winCondition;
    }

    public int getSurviveSeconds() {
        return surviveSeconds;
    }

    // The frame the survive timer runs out on at this tick rate; never for any other win condition.
    public int getSurviveFrameThreshold(int tickRate) {
        return winCondition == PveWinCondition.Survive ? surviveSeconds * tickRate : Integer.MAX_VALUE;
    }

    public int getObjectivesTotal() {
        return objectiveInstallerIds.size();
    }

    // Objectives that are not terminal yet, counting one that is not installed yet.
    public int countObjectivesRemaining() {
        int remaining = 0;

        for (String installerId : objectiveInstallerIds) {
            GameObject objective = runtime == null ? null : runtime.getInstalledGameObject(installerId);
            if (objective == null) {
                // Not installed yet: this objective blocks the win.
                remaining++;
                continue;
            }
            if (seenObjectives.contains(objective.getId())) {
                continue;
            }
            if (PveObjectiveTarget.isTerminal(objective)) {
                seenObjectives.add(objective.getId());
                continue;
            }

            remaining++;
        }

        return remaining;
    }

    private boolean areAllObjectivesTerminal() {
        if (objectiveInstallerIds.isEmpty() || runtime == null) {
            return false;
        }

        return countObjectivesRemaining() == 0;
    }
}
