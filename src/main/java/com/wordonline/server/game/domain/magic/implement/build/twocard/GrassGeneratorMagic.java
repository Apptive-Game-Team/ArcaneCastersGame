package com.wordonline.server.game.domain.magic.implement.build.twocard;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.implement.build.AbstractSummonMagic;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("grass_generator")
public class GrassGeneratorMagic extends AbstractSummonMagic {
    public GrassGeneratorMagic() {
        super(PrefabType.GrassGenerator);
    }
}
