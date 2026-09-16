package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WindBladeShotTest {

    @Test
    void damagesTargetWithoutApplyingKnockback() {
        GameObject shotObject = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        Damageable damageable = mock(Damageable.class);

        when(shotObject.getMaster()).thenReturn(Master.LeftPlayer);
        when(shotObject.getElement()).thenReturn(new Element());
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getComponents(Damageable.class)).thenReturn(List.of(damageable));

        new WindBladeShot(shotObject, 9, 5f).onCollisionWithEnemy(target);

        verify(damageable).onDamaged(org.mockito.ArgumentMatchers.any());
        verify(target, never()).getComponent(EffectReceiver.class);
    }
}
