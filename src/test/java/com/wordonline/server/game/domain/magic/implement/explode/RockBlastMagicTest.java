package com.wordonline.server.game.domain.magic.implement.explode;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RockBlastMagicTest {

    @Test
    void consumesEveryRemnantAndLeavesSmallRemnantAfterMediumRemnantExplodes() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters rockBlastParameters = mock(GameObjectParameters.class);
        GameContext context = mock(GameContext.class);
        when(parameters.object(GameObjectKey.ROCK_BLAST)).thenReturn(rockBlastParameters);
        when(rockBlastParameters.floatValue(ParameterKey.RADIUS)).thenReturn(3f);

        GameObject small = active(PrefabType.RockRemnant, new Vector3(2f, 0f, 3f), context);
        GameObject medium = active(PrefabType.MediumRockRemnant, new Vector3(4f, 0f, 5f), context);
        GameObject unrelated = active(PrefabType.RockGolem, new Vector3(3f, 0f, 4f), context);
        Vector3 castPosition = new Vector3(3f, 0f, 4f);
        when(context.overlapSphereAll(castPosition, 3f)).thenReturn(List.of(small, medium, unrelated));
        clearInvocations(context);

        new RockBlastMagic(parameters).run(context, Master.RightPlayer, castPosition);

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(context, times(3)).createGameObject(created.capture());
        List<GameObject> explosions = created.getAllValues().stream()
                .filter(object -> object.getType() == PrefabType.RockExplode)
                .toList();
        assertThat(explosions).allMatch(object -> object.getMaster() == Master.RightPlayer);
        assertThat(explosions).extracting(GameObject::getPosition)
                .containsExactly(new Vector3(2f, 0f, 3f), new Vector3(4f, 0f, 5f));
        assertThat(created.getAllValues()).filteredOn(object -> object.getType() == PrefabType.RockRemnant)
                .singleElement()
                .satisfies(remainder -> {
                    assertThat(remainder.getMaster()).isEqualTo(Master.LeftPlayer);
                    assertThat(remainder.getPosition()).isEqualTo(new Vector3(4f, 0f, 5f));
                });
        assertThat(small.isDestroyed()).isTrue();
        assertThat(medium.isDestroyed()).isTrue();
        assertThat(unrelated.isDestroyed()).isFalse();
    }

    private GameObject active(PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(Master.LeftPlayer, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
