package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.BeanProjectile;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BeanShooterMobTest {

    @Test
    void targetsGroundOnly() {
        GameContext context = mock(GameContext.class);
        GameObject shooter = new GameObject(Master.LeftPlayer, PrefabType.BeanShooter, Vector3.ZERO, context);

        BeanShooterMob mob = new BeanShooterMob(shooter, 260, 0.6f, 130, 2.2f, 6.5f);

        assertThat((int) ReflectionTestUtils.getField(mob, "targetMask")).isEqualTo(TargetMask.GROUND.bit);
    }

    @Test
    void firesBeanAtGroundedTargetWithShooterDamageAndTravelLimit() {
        GameContext context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(1f);
        List<GameObject> beans = new ArrayList<>();
        // stands in for BeanPrefabInitializer, which a mocked context never runs
        doAnswer(invocation -> {
            GameObject created = invocation.getArgument(0);
            if (created.getType() == PrefabType.Bean) {
                created.getComponents().add(new BeanProjectile(created, 7f));
                beans.add(created);
            }
            return null;
        }).when(context).createGameObject(any());
        GameObject shooter = new GameObject(Master.LeftPlayer, PrefabType.BeanShooter, Vector3.ZERO, context);
        GameObject target = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(4f, 0f, 3f), context);
        target.setStatus(Status.Idle);
        BeanShooterMob mob = new BeanShooterMob(shooter, 260, 0.6f, 130, 2.2f, 2f);

        assertThat(mob.behavior.test(target)).isTrue();

        assertThat(beans).hasSize(1);
        GameObject bean = beans.get(0);
        assertThat(shooter.getStatus()).isEqualTo(Status.Attack);

        BeanProjectile projectile = bean.getComponent(BeanProjectile.class);
        projectile.update();
        assertThat(bean.getPosition().getX()).isGreaterThan(0f);
        assertThat(bean.getPosition().getZ()).isGreaterThan(0f);
        // 7 units per tick against 1.5 * 2 = 3 units of travel: gone after the first tick
        assertThat(bean.isDestroyed()).isTrue();
    }
}
