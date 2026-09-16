package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DistanceSelfDestroyerTest {

    @Test
    void survivesUntilItHasTravelledTheWholeDistance() {
        GameObject moving = movingObject(new Vector3(4f, 0f, 5f));
        DistanceSelfDestroyer selfDestroyer = new DistanceSelfDestroyer(moving, moving.getPosition(), 8f);

        moving.setPosition(new Vector3(11f, 0f, 5f));
        selfDestroyer.update();

        assertThat(moving.isDestroyed()).isFalse();
    }

    @Test
    void destroysTheObjectOnceItReachesTheDistance() {
        GameObject moving = movingObject(new Vector3(4f, 0f, 5f));
        DistanceSelfDestroyer selfDestroyer = new DistanceSelfDestroyer(moving, moving.getPosition(), 8f);

        moving.setPosition(new Vector3(12f, 0f, 5f));
        selfDestroyer.update();

        assertThat(moving.isDestroyed()).isTrue();
    }

    // Vector3 is mutable and the caller usually hands in the object's own position vector, so the
    // origin is copied. Held by reference, a later mutation would move the start line with it and
    // the object would fly forever.
    @Test
    void keepsItsOriginWhenTheVectorItWasGivenIsMutated() {
        Vector3 start = new Vector3(4f, 0f, 5f);
        GameObject moving = movingObject(new Vector3(start));
        DistanceSelfDestroyer selfDestroyer = new DistanceSelfDestroyer(moving, start, 8f);

        start.add(new Vector3(8f, 0f, 0f));
        moving.setPosition(new Vector3(12f, 0f, 5f));
        selfDestroyer.update();

        assertThat(moving.isDestroyed()).isTrue();
    }

    private GameObject movingObject(Vector3 position) {
        return new GameObject(Master.LeftPlayer, PrefabType.DragonFlame, position, mock(GameContext.class));
    }
}
