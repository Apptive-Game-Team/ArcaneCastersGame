package com.wordonline.server.game.domain.pve;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

// Installs a new object like a scenario installer row, registered under installerId so later
// events, speakers and objectives can reference it. maxHp overrides hp; null keeps the prefab's.
public record PveInstallObjectAction(
        String installerId,
        PrefabType prefabType,
        int positionX,
        int positionZ,
        Integer maxHp
) implements PveScenarioAction {
}
