package com.wordonline.server.game.domain.object.prefab.implement.water;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("ground_tidal_warhead_prefab")
public class GroundTidalWarheadPrefabInitializer extends TidalWarheadPrefabInitializer {

    public GroundTidalWarheadPrefabInitializer(Parameters parameters) {
        super(PrefabType.GroundTidalWarhead, parameters);
    }
}
