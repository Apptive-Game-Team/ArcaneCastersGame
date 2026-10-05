package com.wordonline.server.game.domain.pve;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

// Spawns count objects of prefabType at (positionX, 0, positionZ), 0.5 units apart on x,
// the same burst spacing Spawner uses.
public record PveSpawnWaveAction(
        PrefabType prefabType,
        int count,
        int positionX,
        int positionZ
) implements PveScenarioAction {
}
