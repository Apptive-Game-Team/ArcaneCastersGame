package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.CraterShell;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;
import java.util.EnumSet;

@Component("crater_shell_prefab")
public class CraterShellPrefabInitializer extends PrefabInitializer {
    private final Parameters parameters;

    public CraterShellPrefabInitializer(Parameters parameters) {
        super(PrefabType.CraterShell);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject object) {
        var stats = parameters.object(GameObjectKey.CRATER_SHELL);
        object.setElement(EnumSet.of(ElementType.FIRE, ElementType.ROCK));
        object.addComponent(new CraterShell(object, stats.floatValue(ParameterKey.SPEED),
                stats.floatValue(ParameterKey.RADIUS)));
    }
}
