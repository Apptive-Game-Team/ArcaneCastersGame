package com.wordonline.server.preview;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PreviewRecorder {
    private static final Set<String> REPRESENTATIVE = Set.of("fire_shot", "rock_golem", "wind_spirit", "towerback",
            "healing_totem", "lightning_explosion", "sand_storm", "frenzy_totem", "earth_call",
            "water_shot", "lightning_shot", "wind_blade", "rock_rolling", "fire_drop", "wind_drop",
            "water_explosion", "wind_explosion", "rock_blast", "mini_rock_swarm", "thunder_bird_swarm", "water_slime_swarm");
    private final Map<String, Class<? extends PrefabInitializer>> prefabs = new HashMap<>();
    private final PreviewProperties limits;

    public PreviewRecorder(Map<String, PrefabInitializer> initializers, PreviewProperties limits) {
        initializers.forEach((name, initializer) -> prefabs.put(name, initializer.getClass()));
        this.limits = limits;
    }

    public ObjectNode record(String name, Map<String, Double> inputs, Map<String, Magic> magics) {
        Map<String, ObjectNode> clips = REPRESENTATIVE.contains(name)
                ? new RepresentativePreviewScenarios(name, inputs, prefabs, magics, limits).captureAll()
                : new RemainingPreviewScenarios(name, inputs, prefabs, magics, limits).captureAll();
        ObjectNode clip = clips.get(name);
        if (clip == null || clip.path("scenarios").isEmpty())
            throw new IllegalArgumentException("No preview scenario registered for " + name);
        return clip;
    }
}
