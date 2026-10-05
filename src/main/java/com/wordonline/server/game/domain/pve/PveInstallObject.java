package com.wordonline.server.game.domain.pve;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

// maxHp is null when the prefab's own parameter hp should be used unchanged, and an override
// value when this installer (or an InstallObject action) needs a different boss hp.
public record PveInstallObject(
        String installerId,
        PrefabType prefabType,
        Master master,
        Vector3 position,
        Integer maxHp
) {
}
