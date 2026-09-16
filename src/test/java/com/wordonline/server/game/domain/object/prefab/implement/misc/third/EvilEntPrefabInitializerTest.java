package com.wordonline.server.game.domain.object.prefab.implement.misc.third;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.EvilEntMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvilEntPrefabInitializerTest {

    @Test
    void buildsARangedNatureFireEntFromTheEvilEntParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters evilEnt = mock(GameObjectParameters.class);
        GameObject entObject = mock(GameObject.class);

        when(parameters.object(GameObjectKey.EVIL_ENT)).thenReturn(evilEnt);
        when(evilEnt.intValue(ParameterKey.MASS)).thenReturn(10);
        when(evilEnt.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(evilEnt.intValue(ParameterKey.HP)).thenReturn(180);
        when(evilEnt.floatValue(ParameterKey.SPEED)).thenReturn(0.45f);
        when(evilEnt.intValue(ParameterKey.DAMAGE)).thenReturn(9);
        when(evilEnt.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1.8f);
        when(evilEnt.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(5f);
        when(evilEnt.floatValue(ParameterKey.PROJECTILE_SPEED)).thenReturn(14f);
        when(evilEnt.intValue(ParameterKey.SUB_DAMAGE)).thenReturn(28);
        when(evilEnt.floatValue(ParameterKey.SUB_ATTACK_RANGE)).thenReturn(6f);
        when(evilEnt.floatValue(ParameterKey.SUB_ATTACK_INTERVAL)).thenReturn(12f);
        when(evilEnt.floatValue(ParameterKey.PULL_MASS_LIMIT)).thenReturn(5f);
        when(entObject.getPosition()).thenReturn(Vector3.ZERO);
        when(entObject.getGameContext()).thenReturn(mock(GameContext.class));

        new EvilEntPrefabInitializer(parameters).initialize(entObject);

        ArgumentCaptor<Component> components = ArgumentCaptor.forClass(Component.class);
        verify(entObject, org.mockito.Mockito.atLeastOnce()).addComponent(components.capture());
        List<Component> added = components.getAllValues();

        RigidBody rigidBody = only(added, RigidBody.class);
        assertThat(rigidBody.getMass()).isEqualTo(10);
        assertThat(only(added, ZPhysics.class)).isNotNull();
        assertThat(only(added, CommonEffectReceiver.class)).isNotNull();

        EvilEntMob mob = only(added, EvilEntMob.class);
        assertThat(mob.getMaxHp()).isEqualTo(180);
        assertThat(mob.getSpeed().total()).isEqualTo(0.45f);
        assertThat(mob.getAttackInterval().total()).isEqualTo(1.8f);
        assertThat(ReflectionTestUtils.getField(mob, "attackRange")).isEqualTo(5f);
        assertThat(ReflectionTestUtils.getField(mob, "damage")).isEqualTo(9);
        assertThat(ReflectionTestUtils.getField(mob, "projectileSpeed")).isEqualTo(14f);
        assertThat(ReflectionTestUtils.getField(mob, "subDamage")).isEqualTo(28);
        assertThat(ReflectionTestUtils.getField(mob, "subAttackRange")).isEqualTo(6f);
        assertThat(ReflectionTestUtils.getField(mob, "subAttackInterval")).isEqualTo(12f);
        assertThat(ReflectionTestUtils.getField(mob, "pullMassLimit")).isEqualTo(5f);

        Object detector = ReflectionTestUtils.getField(mob, "detector");
        assertThat(ReflectionTestUtils.getField(detector, "targetMask")).isEqualTo(TargetMask.ANY.bit);

        ArgumentCaptor<CircleCollider> collider = ArgumentCaptor.forClass(CircleCollider.class);
        verify(entObject).addCollider(collider.capture());
        assertThat(collider.getValue().getRadius()).isEqualTo(1.2f);
        assertThat(collider.getValue().isTrigger()).isFalse();

        // setElement is additive, so the ent keeps both halves of what it is.
        verify(entObject).setElement(EnumSet.of(ElementType.NATURE, ElementType.FIRE));
    }

    private <T extends Component> T only(List<Component> components, Class<T> type) {
        return components.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}
