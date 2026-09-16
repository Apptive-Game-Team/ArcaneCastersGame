package com.wordonline.server.game.domain.magic.implement.drop;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("lightning_drop")
public class LightningDropMagic extends AbstractDropMagic {

    public LightningDropMagic() {
        super(PrefabType.LightningCloud, 0f);
    }
}
