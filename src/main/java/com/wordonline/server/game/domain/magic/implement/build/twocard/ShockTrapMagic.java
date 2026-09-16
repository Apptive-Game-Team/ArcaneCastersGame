package com.wordonline.server.game.domain.magic.implement.build.twocard;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.implement.build.AbstractSummonMagic;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("shock_trap")
public class ShockTrapMagic extends AbstractSummonMagic {
    public ShockTrapMagic() {
        super(PrefabType.ShockTrap);
    }
}
