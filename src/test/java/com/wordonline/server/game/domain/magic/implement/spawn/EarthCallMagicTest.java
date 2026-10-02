package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EarthCallMagicTest {

    @Test
    void convertsSmallAndMediumRemnantsIntoMatchingOwnedSummons() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters earthCallParameters = mock(GameObjectParameters.class);
        GameContext context = mock(GameContext.class);
        when(parameters.object(GameObjectKey.EARTH_CALL)).thenReturn(earthCallParameters);
        when(earthCallParameters.floatValue(ParameterKey.RADIUS)).thenReturn(3f);

        GameObject small = active(PrefabType.RockRemnant, Master.RightPlayer, new Vector3(2f, 0f, 3f), context);
        GameObject medium = active(PrefabType.MediumRockRemnant, Master.LeftPlayer, new Vector3(4f, 0f, 5f), context);
        GameObject unrelated = active(PrefabType.RockSlime, Master.RightPlayer, new Vector3(3f, 0f, 4f), context);
        Vector3 castPosition = new Vector3(3f, 0f, 4f);
        when(context.overlapSphereAll(castPosition, 3f)).thenReturn(List.of(small, medium, unrelated));
        clearInvocations(context);

        new EarthCallMagic(parameters).run(context, Master.LeftPlayer, castPosition);

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(context, org.mockito.Mockito.times(3)).createGameObject(created.capture());
        assertThat(created.getAllValues()).extracting(GameObject::getType)
                .containsExactly(PrefabType.EarthCall, PrefabType.MiniRock, PrefabType.RockGolem);
        assertThat(created.getAllValues().subList(1, 3)).allMatch(object -> object.getMaster() == Master.LeftPlayer);
        assertThat(created.getAllValues().get(1).getPosition()).isEqualTo(new Vector3(2f, 0f, 3f));
        assertThat(created.getAllValues().get(2).getPosition()).isEqualTo(new Vector3(4f, 0f, 5f));
        assertThat(small.isDestroyed()).isTrue();
        assertThat(medium.isDestroyed()).isTrue();
        assertThat(unrelated.isDestroyed()).isFalse();
    }

    private GameObject active(PrefabType type, Master master, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
