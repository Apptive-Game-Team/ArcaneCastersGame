package com.wordonline.server.game.domain.pve;

// Pulls every live LeftPlayer summon toward the object installed under installerId over about
// 1.5 seconds, then destroys it. If that installer is missing, the summons are destroyed in place.
public record PvePullPlayerUnitsAction(String installerId) implements PveScenarioAction {
}
