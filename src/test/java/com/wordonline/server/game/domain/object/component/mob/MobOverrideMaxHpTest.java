package com.wordonline.server.game.domain.object.component.mob;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MobOverrideMaxHpTest {

    @Test
    void overridesBothMaxHpAndCurrentHpAndTellsTheClient() {
        GameContext gameContext = mock(GameContext.class);
        GameObject gameObject = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, gameContext);
        TestMob mob = new TestMob(gameObject, 100);
        gameObject.addComponent(mob);
        gameObject.flushComponents();

        mob.overrideMaxHp(500);

        assertThat(mob.getMaxHp()).isEqualTo(500);
        assertThat(mob.getHp()).isEqualTo(500);
        // applyUpdate() queues the new gauge so the client learns the overridden hp this frame,
        // rather than only after the next damage event.
        verify(gameContext).updateGameObject(gameObject);
    }

    @Test
    void aNonPositiveOverrideIsIgnored() {
        GameContext gameContext = mock(GameContext.class);
        GameObject gameObject = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, gameContext);
        TestMob mob = new TestMob(gameObject, 100);
        gameObject.addComponent(mob);
        gameObject.flushComponents();

        mob.overrideMaxHp(0);
        mob.overrideMaxHp(-10);

        assertThat(mob.getMaxHp()).isEqualTo(100);
        assertThat(mob.getHp()).isEqualTo(100);
    }

    private static class TestMob extends Mob {
        private TestMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp, 0f);
        }

        @Override
        public void onDeath() {
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }
}
