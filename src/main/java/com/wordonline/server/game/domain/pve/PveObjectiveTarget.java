package com.wordonline.server.game.domain.pve;

import com.wordonline.server.game.domain.object.GameObject;

public interface PveObjectiveTarget {
    boolean isTerminal();

    // An object counts as terminal once it is fully removed from the world, or once one of its
    // components (a boss's PVEBossMob, for instance) says it already crossed its own end state.
    static boolean isTerminal(GameObject gameObject) {
        if (gameObject == null || gameObject.isDestroyed()) {
            return true;
        }
        return gameObject.getComponents(PveObjectiveTarget.class).stream()
                .anyMatch(PveObjectiveTarget::isTerminal);
    }
}
