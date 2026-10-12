package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SelfDestructMob;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PveCorruptedSpiritPrefabInitializerTest {

    private Parameters parameters;
    private GameContext gameContext;

    @BeforeEach
    void setUp() {
        parameters = mock(Parameters.class);
        gameContext = mock(GameContext.class);
        GameObjectParameters vine = spirit();
        GameObjectParameters wind = spirit();
        when(parameters.object(GameObjectKey.PVE_CORRUPTED_VINE_SPIRIT)).thenReturn(vine);
        when(parameters.object(GameObjectKey.PVE_CORRUPTED_WIND_SPIRIT)).thenReturn(wind);
    }

    private static GameObjectParameters spirit() {
        GameObjectParameters p = mock(GameObjectParameters.class);
        when(p.intValue(ParameterKey.HP)).thenReturn(40);
        when(p.intValue(ParameterKey.DAMAGE)).thenReturn(8);
        when(p.intValue(ParameterKey.MASS)).thenReturn(1);
        when(p.floatValue(ParameterKey.SPEED)).thenReturn(1f);
        when(p.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1f);
        when(p.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(1.5f);
        when(p.floatValue(ParameterKey.RADIUS)).thenReturn(0.4f);
        return p;
    }

    @Test
    void registersUnderItsOwnBeanNames() {
        assertThat(PrefabType.PveCorruptedVineSpirit.getBeanName()).isEqualTo("pve_corrupted_vine_spirit_prefab");
        assertThat(PrefabType.PveCorruptedWindSpirit.getBeanName()).isEqualTo("pve_corrupted_wind_spirit_prefab");
    }

    @Test
    void corruptedVineSpiritIsAGroundMeleeMob() {
        GameObject go = new GameObject(Master.RightPlayer, PrefabType.PveCorruptedVineSpirit, new Vector3(14, 0, 5), gameContext);
        new PveCorruptedVineSpiritPrefabInitializer(parameters).initialize(go);
        go.flushComponents();

        assertThat(go.getComponent(MeleeAttackMob.class)).isNotNull();
        assertThat(go.getComponent(ZPhysics.class).isAerial()).isFalse();
    }

    @Test
    void corruptedWindSpiritIsAnAerialSelfDestructMob() {
        GameObject go = new GameObject(Master.RightPlayer, PrefabType.PveCorruptedWindSpirit, new Vector3(14, 0, 5), gameContext);
        new PveCorruptedWindSpiritPrefabInitializer(parameters).initialize(go);
        go.flushComponents();

        assertThat(go.getComponent(SelfDestructMob.class)).isNotNull();
        assertThat(go.getComponent(ZPhysics.class).isAerial()).isTrue();
    }
}
