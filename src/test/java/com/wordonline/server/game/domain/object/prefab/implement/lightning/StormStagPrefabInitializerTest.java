package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StormStagPrefabInitializerTest {

    @Test
    void startsPermanentlyOvercharged() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters stormStagParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.STORM_STAG)).thenReturn(stormStagParameters);
        GameObject stormStag = new GameObject(
                Master.LeftPlayer,
                PrefabType.StormStag,
                Vector3.ZERO,
                mock(GameContext.class));

        new StormStagPrefabInitializer(parameters).initialize(stormStag);

        OverchargeStatusEffect overcharge = stormStag.getComponentsToAdd().stream()
                .filter(OverchargeStatusEffect.class::isInstance)
                .map(OverchargeStatusEffect.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(overcharge.getRemaining()).isPositive().isInfinite();
        assertThat(stormStag.getEffects()).contains(Effect.Overcharge);
    }
}
