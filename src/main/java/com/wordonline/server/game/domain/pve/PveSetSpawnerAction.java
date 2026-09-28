package com.wordonline.server.game.domain.pve;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

// Replaces the periodic Spawner on installer installerId. count = 0 removes it (the boss stops
// summoning); prefabType and intervalSeconds only matter when count > 0.
public record PveSetSpawnerAction(
        String installerId,
        int count,
        PrefabType prefabType,
        Float intervalSeconds
) implements PveScenarioAction {
}
