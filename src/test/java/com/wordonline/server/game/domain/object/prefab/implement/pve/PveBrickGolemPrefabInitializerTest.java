package com.wordonline.server.game.domain.object.prefab.implement.pve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.FlowFieldNavigation;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveBrickGolemMob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;

import java.util.List;

class PveBrickGolemPrefabInitializerTest {

    @Test
    void buildsAStationaryRockBossFromItsOwnParameterObject() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters p = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.PVE_BRICK_GOLEM)).thenReturn(p);
        when(p.intValue(ParameterKey.HP)).thenReturn(4000);
        when(p.intValue(ParameterKey.MASS)).thenReturn(-1);
        when(p.floatValue(ParameterKey.RADIUS)).thenReturn(2f);
        when(p.intValue(ParameterKey.DAMAGE)).thenReturn(30);
        when(p.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(4f);
        when(p.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(12f);
        when(p.floatValue(ParameterKey.EFFECT_RADIUS)).thenReturn(2f);
        when(p.intValue(ParameterKey.SUB_DAMAGE)).thenReturn(12);
        when(p.floatValue(ParameterKey.SUB_ATTACK_INTERVAL)).thenReturn(3f);
        when(p.floatValue(ParameterKey.SUB_ATTACK_RANGE)).thenReturn(4f);
        GameContext gameContext = mock(GameContext.class);
        GameSessionData sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getNavigation()).thenReturn(new FlowFieldNavigation(() -> 0, List::of));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(new ObjectsInfoDtoBuilder(gameContext));
        GameObject boss = new GameObject(Master.RightPlayer, PrefabType.PveBrickGolem, new Vector3(14, 0, 5), gameContext);
        boss.setStatus(Status.Idle);

        new PveBrickGolemPrefabInitializer(parameters).initialize(boss);
        boss.flushComponents();

        Mob mob = PveScenarioInstaller.findHealthMob(boss);
        assertThat(mob).isInstanceOf(PveBrickGolemMob.class);
        assertThat(mob.getMaxHp()).isEqualTo(4000);
        assertThat(mob.getSpeed().total()).isZero();
        assertThat(boss.getComponents(Collidable.class)).isNotEmpty();
        assertThat(boss.getElement().nativeHas(ElementType.ROCK)).isTrue();
        assertThat(boss.getComponent(Spawner.class)).isNull();
        assertThat(PrefabType.PveBrickGolem.getBeanName()).isEqualTo("pve_brick_golem_prefab");
        assertThat(GameObjectKey.PVE_BRICK_GOLEM.dbName()).isEqualTo("pve_brick_golem");
    }
}
