package com.wordonline.server.game.domain.pve;

import java.util.List;

public record PveScenario(
        List<String> objectiveInstallerIds,
        List<PveInstallObject> installers,
        List<PveScenarioEvent> events,
        PveScenarioRules rules,
        List<PveShield> shields
) {
    // A scenario without shield rows.
    public PveScenario(
            List<String> objectiveInstallerIds,
            List<PveInstallObject> installers,
            List<PveScenarioEvent> events,
            PveScenarioRules rules
    ) {
        this(objectiveInstallerIds, installers, events, rules, List.of());
    }
}
