package com.wordonline.server.game.domain.object.component.effect.statuseffect;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.service.GameContext;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnockbackStatusEffectTest {

    @Test
    void movesAndLiftsByInverseSquareRootOfMass() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        RigidBody rigidBody = mock(RigidBody.class);
        ZPhysics zPhysics = mock(ZPhysics.class);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(RigidBody.class)).thenReturn(rigidBody);
        when(owner.getComponent(ZPhysics.class)).thenReturn(zPhysics);
        when(rigidBody.getMass()).thenReturn(10);
        when(gameContext.getDeltaTime()).thenReturn(0.1f);

        KnockbackStatusEffect knockback =
                new KnockbackStatusEffect(owner, Vector3.RIGHT, 1f, StatusEffectKey.Knockback_Receive);
        knockback.start();
        for (int i = 0; i < 6; i++) {
            knockback.update();
        }

        ArgumentCaptor<Vector3> velocityCaptor = ArgumentCaptor.forClass(Vector3.class);
        verify(rigidBody, org.mockito.Mockito.times(5)).addVelocity(velocityCaptor.capture());
        float movedDistance = velocityCaptor.getAllValues().stream()
                .map(Vector3::getX)
                .reduce(0f, Float::sum) * 0.1f;
        assertThat(movedDistance).isCloseTo(2f / (float) Math.sqrt(10f),
                org.assertj.core.data.Offset.offset(0.0001f));

        ArgumentCaptor<Float> impulseCaptor = ArgumentCaptor.forClass(Float.class);
        verify(zPhysics, org.mockito.Mockito.times(2)).addImpulseZ(impulseCaptor.capture());
        assertThat(impulseCaptor.getAllValues().get(0))
                .isCloseTo(10f / (float) Math.sqrt(10f), org.assertj.core.data.Offset.offset(0.0001f));
        assertThat(impulseCaptor.getAllValues().get(1))
                .isCloseTo(-10f / (float) Math.sqrt(10f), org.assertj.core.data.Offset.offset(0.0001f));
    }

    @Test
    void expirationKeepsSpeedModifierOwnedByAnotherStatusEffect() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Mob mob = mock(Mob.class);
        Stat speed = new Stat(10f);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(speed);
        when(gameContext.getDeltaTime()).thenReturn(1f);

        SnaredStatusEffect snare = new SnaredStatusEffect(owner, 3f, 0, 0.5f, StatusEffectKey.Snared_Receive);
        snare.start();
        assertThat(speed.total()).isEqualTo(5f);

        KnockbackStatusEffect knockback =
                new KnockbackStatusEffect(owner, new Vector3(1f, 0f, 0f), 1f, StatusEffectKey.Knockback_Receive);
        knockback.start();
        knockback.update();
        knockback.update();

        verify(owner).removeComponent(knockback);
        assertThat(speed.total()).isEqualTo(5f);
    }
}
