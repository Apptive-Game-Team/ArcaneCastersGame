package com.wordonline.server.game.domain.pve;

// One row of pve_scenario_rules, or the default when a scenario has none: DestroyObjectives,
// today's behavior. surviveSeconds is required when winCondition is Survive.
public record PveScenarioRules(
        PveWinCondition winCondition,
        Integer surviveSeconds
) {
    public static PveScenarioRules defaultRules() {
        return new PveScenarioRules(PveWinCondition.DestroyObjectives, null);
    }
}
