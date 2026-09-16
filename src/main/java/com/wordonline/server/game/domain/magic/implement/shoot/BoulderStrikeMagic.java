package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("boulder_strike")
public class BoulderStrikeMagic extends AbstractShotMagic {

    public BoulderStrikeMagic() {
        super(PrefabType.BoulderStrike);
    }
}
