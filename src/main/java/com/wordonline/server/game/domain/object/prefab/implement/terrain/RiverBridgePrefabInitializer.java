package com.wordonline.server.game.domain.object.prefab.implement.terrain;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * One 1 by 1 cell of the river map. It carries no components, collider or hp, so nothing can
 * target, damage or collide with it and it never expires. It exists only so that the client,
 * which draws what it is told, learns where the cell is. Where water stops ground bodies is
 * decided by {@code Terrain}, not by this object.
 */
@Component("river_bridge_prefab")
public class RiverBridgePrefabInitializer extends PrefabInitializer {

    public RiverBridgePrefabInitializer() {
        super(PrefabType.RiverBridge);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.setElement(ElementType.NONE);
    }
}
