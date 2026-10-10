package com.wordonline.server.game.dto.frame;

import java.util.List;

import com.wordonline.server.game.domain.debug.Gizmo;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

// This class is used to send created object information to the client
public record CreatedObjectDto(
        int id,
        PrefabType type,
        Vector3 position,
        Master master,
        List<Gizmo> gizmos
) {
}
