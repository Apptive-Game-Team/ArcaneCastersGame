package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BehaviorMobTargetValidityTest {

    @Test
    void airOnlyMobReleasesTargetAfterItLands() {
        GameObject target = targetAt(GameConfig.AERIAL_MOB_INIT_HEIGHT);
        TestBehaviorMob mob = mobWithMask(TargetMask.AIR.bit);

        assertThat(mob.isTargetValid(target)).isTrue();

        when(target.getPosition()).thenReturn(Vector3.ZERO);

        assertThat(mob.isTargetValid(target)).isFalse();
    }

    @Test
    void groundOnlyMobReleasesTargetAfterItBecomesAirborne() {
        GameObject target = targetAt(0f);
        TestBehaviorMob mob = mobWithMask(TargetMask.GROUND.bit);

        assertThat(mob.isTargetValid(target)).isTrue();

        when(target.getPosition()).thenReturn(new Vector3(0f, GameConfig.AERIAL_STANDARD_HEIGHT, 0f));

        assertThat(mob.isTargetValid(target)).isFalse();
    }

    private TestBehaviorMob mobWithMask(int targetMask) {
        GameObject self = mock(GameObject.class);
        when(self.getGameContext()).thenReturn(mock(GameContext.class));
        when(self.getMaster()).thenReturn(Master.LeftPlayer);
        return new TestBehaviorMob(self, targetMask);
    }

    private GameObject targetAt(float y) {
        GameObject target = mock(GameObject.class);
        when(target.getStatus()).thenReturn(Status.Idle);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(new Vector3(0f, y, 0f));
        return target;
    }

    private static class TestBehaviorMob extends BehaviorMob {
        private TestBehaviorMob(GameObject gameObject, int targetMask) {
            super(gameObject, 10, 1f, targetMask, 1f, 1f, target -> true);
        }

        private boolean isTargetValid(GameObject target) {
            return isValidTarget(target);
        }
    }
}
