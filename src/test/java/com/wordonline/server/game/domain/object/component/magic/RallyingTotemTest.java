package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.service.ManaCharger;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Item;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.directive.RallyMoveDirective;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.prefab.implement.drop.RallyingTotemPrefabInitializer;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RallyingTotemTest {

    private static final float FALL_SPEED = 5f;
    private static final float DURATION = 10f;
    private static final float ZONE_RADIUS = 3f;
    private static final float DETECTION_RANGE = 8f;
    private static final float BUFF_DURATION = 2f;
    private static final float TICK = 0.5f;
    private static final float DROP_HEIGHT = 4f;

    private GameContext gameContext;
    private GameSessionData sessionData;
    private GameObject totem;
    private RallyingTotem rallyingTotem;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(
                new PlayerData(mock(ManaCharger.class)), new PlayerData(mock(ManaCharger.class)));
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(TICK);

        totem = mock(GameObject.class);
        when(totem.getGameContext()).thenReturn(gameContext);
        when(totem.getMaster()).thenReturn(Master.LeftPlayer);
        Vector3[] position = {new Vector3(0f, DROP_HEIGHT, 0f)};
        when(totem.getPosition()).thenAnswer(call -> position[0]);
        org.mockito.Mockito.doAnswer(call -> {
            position[0] = call.getArgument(0);
            return null;
        }).when(totem).setPosition(any(Vector3.class));

        rallyingTotem = new RallyingTotem(
                totem, FALL_SPEED, DURATION, ZONE_RADIUS, DETECTION_RANGE, BUFF_DURATION);
    }

    private GameObject alliedMobAt(float x) {
        GameObject mob = mock(GameObject.class);
        when(mob.getMaster()).thenReturn(Master.LeftPlayer);
        when(mob.getPosition()).thenReturn(new Vector3(x, 0f, 0f));
        when(mob.hasComponent(BehaviorMob.class)).thenReturn(true);
        when(mob.getComponentsToAdd()).thenReturn(new ArrayList<>());
        sessionData.gameObjects.add(mob);
        return mob;
    }

    private EffectReceiver alliedReceiverInZone() {
        GameObject ally = mock(GameObject.class);
        EffectReceiver receiver = mock(EffectReceiver.class);
        when(ally.getMaster()).thenReturn(Master.LeftPlayer);
        when(ally.getComponent(EffectReceiver.class)).thenReturn(receiver);
        when(gameContext.overlapSphereAll(totem, ZONE_RADIUS)).thenReturn(List.of(ally));
        return receiver;
    }

    @Test
    void rallysNobodyAndBuffsNobodyWhileFalling() {
        GameObject mob = alliedMobAt(1f);
        EffectReceiver receiver = alliedReceiverInZone();

        rallyingTotem.update();

        assertThat(totem.getPosition().getY()).isEqualTo(DROP_HEIGHT - FALL_SPEED * TICK);
        verify(mob, never()).addComponent(any());
        verify(receiver, never()).onReceive(any(Effect.class), anyFloat());
    }

    @Test
    void landingSnapsToGroundAndStartsRallyingOnTheNextTick() {
        GameObject mob = alliedMobAt(1f);
        EffectReceiver receiver = alliedReceiverInZone();

        rallyingTotem.update();
        rallyingTotem.update();
        assertThat(totem.getPosition().getY()).isEqualTo(0.0f);
        verify(mob, never()).addComponent(any());

        rallyingTotem.update();
        verify(mob).addComponent(any(RallyMoveDirective.class));
        verify(receiver).onReceive(Effect.Inspired, BUFF_DURATION);
    }

    @Test
    void durationStartsCountingAtLanding() {
        rallyingTotem.update();
        rallyingTotem.update();
        for (int i = 0; i < (int) (DURATION / TICK) - 1; i++) {
            rallyingTotem.update();
        }
        verify(totem, never()).destroy();

        rallyingTotem.update();
        verify(totem).destroy();
    }

    @Test
    void recruitsOnlyAlliedMobsWithinDetectionRange() {
        GameObject near = alliedMobAt(DETECTION_RANGE);
        GameObject far = alliedMobAt(DETECTION_RANGE + 0.5f);

        rallyingTotem.update();
        rallyingTotem.update();
        rallyingTotem.update();

        verify(near).addComponent(any(RallyMoveDirective.class));
        verify(far, never()).addComponent(any());
    }

    @Test
    void prefabHasNoItemComponent() {
        Parameters parameters = mock(Parameters.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(parameters.object(GameObjectKey.RALLYING_TOTEM).floatValue(any(ParameterKey.class))).thenReturn(1f);
        GameObject object = new GameObject(Master.LeftPlayer,
                com.wordonline.server.game.domain.object.prefab.PrefabType.RallyingTotem,
                Vector3.ZERO, gameContext);

        new RallyingTotemPrefabInitializer(parameters).initialize(object);

        assertThat(object.getComponentsToAdd()).noneMatch(Item.class::isInstance);
        assertThat(object.getComponentsToAdd()).anyMatch(RallyingTotem.class::isInstance);
    }
}
