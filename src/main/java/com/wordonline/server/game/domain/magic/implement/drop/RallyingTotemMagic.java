package com.wordonline.server.game.domain.magic.implement.drop;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("rallying_totem")
public class RallyingTotemMagic extends AbstractDropMagic {
    public RallyingTotemMagic() {
        super(PrefabType.RallyingTotem);
    }
}
