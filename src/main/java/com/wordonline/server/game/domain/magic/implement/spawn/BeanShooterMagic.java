package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("bean_shooter")
public class BeanShooterMagic extends AbstractSpawnMagic {
    public BeanShooterMagic(Parameters parameters) {
        super(PrefabType.BeanShooter, parameters.object(GameObjectKey.BEAN_SHOOTER));
    }
}
