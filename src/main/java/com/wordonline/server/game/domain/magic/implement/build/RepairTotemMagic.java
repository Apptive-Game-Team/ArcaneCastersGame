package com.wordonline.server.game.domain.magic.implement.build;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("repair_totem")
public class RepairTotemMagic extends AbstractSummonMagic {
    public RepairTotemMagic() {
        super(PrefabType.RepairTotem);
    }
}
