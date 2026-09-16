package com.wordonline.server.game.domain.magic.implement.build.twocard;

import com.wordonline.server.game.domain.magic.implement.build.AbstractSummonMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("dragon_tower")
public class DragonTowerMagic extends AbstractSummonMagic {
    public DragonTowerMagic() {
        super(PrefabType.DragonTower);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        Vector3 groundedPosition = new Vector3(position.getX(), 0f, position.getZ());
        new GameObject(getMaster(master), PrefabType.DragonTower, groundedPosition, gameContext);
    }
}
