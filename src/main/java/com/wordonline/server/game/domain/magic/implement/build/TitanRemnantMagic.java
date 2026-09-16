package com.wordonline.server.game.domain.magic.implement.build;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("titan_remnant")
public class TitanRemnantMagic extends AbstractSummonMagic {

    public TitanRemnantMagic() {
        super(PrefabType.TitanRemnant);
    }
}
