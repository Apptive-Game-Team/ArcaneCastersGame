package com.wordonline.server.game.domain.object.prefab.implement.terrain;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.physic.EdgeCollider;
import com.wordonline.server.game.domain.object.component.physic.WaterBarrier;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * One 1 by 1 cell of the river map. It has no hp, so nothing can target or damage it, and it
 * never expires. Its position is the cell center. It carries one {@link EdgeCollider} on each side
 * that faces land or a bridge cell (see {@link Terrain#exposedSides}), which is what stops ground
 * bodies, and a {@link WaterBarrier} so that only ground bodies are tested against them. The
 * client draws the cell from the object itself.
 */
@Component("river_water_prefab")
public class RiverWaterPrefabInitializer extends PrefabInitializer {

    public RiverWaterPrefabInitializer() {
        super(PrefabType.RiverWater);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.setElement(ElementType.NONE);

        Terrain terrain = gameObject.getGameContext().getTerrain();
        if (terrain == null) {
            return;
        }
        Terrain.Cell cell = new Terrain.Cell(
                (int) Math.floor(gameObject.getPosition().getX()),
                (int) Math.floor(gameObject.getPosition().getZ()));
        for (Terrain.Side side : terrain.exposedSides(cell)) {
            gameObject.addCollider(new EdgeCollider(gameObject, side.start(), side.end(), false));
        }
        gameObject.addComponent(new WaterBarrier(gameObject));
    }
}
