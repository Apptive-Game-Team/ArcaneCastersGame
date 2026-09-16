package com.wordonline.server.game.domain.magic.implement.drop;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LightningDropMagicTest {

    @Test
    void spawnsStormCloudAtGroundHeight() {
        GameContext gameContext = mock(GameContext.class);

        new LightningDropMagic().run(
                gameContext,
                Master.LeftPlayer,
                new Vector3(4f, 7f, 6f)
        );

        ArgumentCaptor<GameObject> gameObjectCaptor = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(gameObjectCaptor.capture());

        GameObject stormCloud = gameObjectCaptor.getValue();
        assertThat(stormCloud.getType()).isEqualTo(PrefabType.LightningCloud);
        assertThat(stormCloud.getPosition())
                .usingRecursiveComparison()
                .isEqualTo(new Vector3(4f, 0f, 6f));
    }
}
