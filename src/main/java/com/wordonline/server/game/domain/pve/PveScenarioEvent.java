package com.wordonline.server.game.domain.pve;

import java.util.List;

// key/lines are null/empty when the event carries no dialogue (message_key is nullable);
// targetInstallerId is required for InstallerHpPercentLte and InstallerDestroyed, unused otherwise.
public record PveScenarioEvent(
        String id,
        PveTriggerType type,
        int value,
        String targetInstallerId,
        String speakerInstallerId,
        String key,
        List<String> lines,
        List<PveScenarioAction> actions
) {
}
