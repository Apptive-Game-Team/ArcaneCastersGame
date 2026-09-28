package com.wordonline.server.game.domain.pve;

import java.util.List;

public record PveScenario(
        List<String> objectiveInstallerIds,
        List<PveInstallObject> installers,
        List<PveScenarioEvent> events,
        PveScenarioRules rules
) {
}
