package com.wordonline.server.game.domain.object.component.build;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.TimedMassPush;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WindPushComponentTest {

    @Test
    void appliesPushEveryHalfSecondToEnemiesOnly() {
        GameObject totem = mock(GameObject.class);
        GameObject enemy = pushableMob(Master.RightPlayer);
        GameObject ally = pushableMob(Master.LeftPlayer);
        GameContext gameContext = mock(GameContext.class);
        Physics physics = mock(Physics.class);

        when(totem.getMaster()).thenReturn(Master.LeftPlayer);
        when(totem.getPosition()).thenReturn(Vector3.ZERO);
        when(totem.getGameContext()).thenReturn(gameContext);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(gameContext.getDeltaTime()).thenReturn(0.25f);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of(enemy, ally));

        WindPushComponent windPush = new WindPushComponent(totem, 10f, 6f, 3f);
        windPush.update();
        windPush.update();
        windPush.update();

        ArgumentCaptor<Component> componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(enemy, times(2)).addComponent(componentCaptor.capture());
        assertThat(componentCaptor.getAllValues()).allMatch(TimedMassPush.class::isInstance);
        verify(ally, never()).addComponent(any());
    }

    @Test
    void pushBoxCoversGroundRangeOnXAndZWithoutSwappingAxes() {
        GameObject totem = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Physics physics = mock(Physics.class);

        when(totem.getMaster()).thenReturn(Master.LeftPlayer);
        when(totem.getPosition()).thenReturn(new Vector3(4f, 0f, 5f));
        when(totem.getGameContext()).thenReturn(gameContext);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(gameContext.getDeltaTime()).thenReturn(0.25f);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of());

        // push_range_x=6 (X), push_range_y=3 (fed as the ground-plane Z depth, per the totem's
        // database parameter naming) -> a 6-wide, 3-deep ground box, not 6 x 1.
        WindPushComponent windPush = new WindPushComponent(totem, 10f, 6f, 3f);
        windPush.update();

        ArgumentCaptor<Vector3> centerCaptor = ArgumentCaptor.forClass(Vector3.class);
        ArgumentCaptor<Vector3> sizeCaptor = ArgumentCaptor.forClass(Vector3.class);
        verify(physics).overlapBoxAll(centerCaptor.capture(), sizeCaptor.capture());

        Vector3 size = sizeCaptor.getValue();
        assertThat(size.getX()).isEqualTo(6f);
        assertThat(size.getZ()).isEqualTo(3f);
        assertThat(size.getY()).isEqualTo(GameConfig.AERIAL_STANDARD_HEIGHT);

        // LeftPlayer's totem pushes towards +X (RIGHT); the box sits ground-up, so its center
        // y is half the box height rather than the totem's own y.
        Vector3 center = centerCaptor.getValue();
        assertThat(center.getX()).isEqualTo(4f + 6f / 2);
        assertThat(center.getY()).isEqualTo(GameConfig.AERIAL_STANDARD_HEIGHT / 2);
        assertThat(center.getZ()).isEqualTo(5f);
    }

    private GameObject pushableMob(Master master) {
        GameObject target = mock(GameObject.class);
        RigidBody rigidBody = mock(RigidBody.class);
        when(target.getMaster()).thenReturn(master);
        when(target.hasComponent(Mob.class)).thenReturn(true);
        when(target.getComponent(RigidBody.class)).thenReturn(rigidBody);
        when(target.getComponents(TimedMassPush.class)).thenReturn(List.of());
        when(target.getComponentsToAdd()).thenReturn(List.of());
        when(rigidBody.getMass()).thenReturn(1);
        return target;
    }
}
