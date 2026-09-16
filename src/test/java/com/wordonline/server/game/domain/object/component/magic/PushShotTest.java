package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.TimedMassPush;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushShotTest {

    @Test
    void refreshesMassScaledPushButDamagesOnlyOnce() {
        GameObject shotObject = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        Damageable damageable = mock(Damageable.class);
        RigidBody rigidBody = mock(RigidBody.class);
        List<Component> componentsToAdd = new ArrayList<>();

        when(shotObject.getMaster()).thenReturn(Master.LeftPlayer);
        when(shotObject.getPosition()).thenReturn(Vector3.ZERO);
        when(shotObject.getElement()).thenReturn(new Element());
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(Vector3.RIGHT);
        when(target.getComponents(Damageable.class)).thenReturn(List.of(damageable));
        when(target.getComponents(TimedMassPush.class)).thenReturn(List.of());
        when(target.getComponentsToAdd()).thenReturn(componentsToAdd);
        when(target.getComponent(RigidBody.class)).thenReturn(rigidBody);
        when(rigidBody.getMass()).thenReturn(10);
        doAnswer(invocation -> componentsToAdd.add(invocation.getArgument(0)))
                .when(target).addComponent(any(Component.class));

        PushShot pushShot = new PushShot(shotObject, 4, 3f);
        pushShot.setTarget(Vector3.RIGHT);
        pushShot.onCollisionWithEnemy(target);
        pushShot.onCollisionWithEnemy(target);

        verify(damageable, times(1)).onDamaged(any());
        assertThat(componentsToAdd).singleElement().isInstanceOf(TimedMassPush.class);
    }
}
