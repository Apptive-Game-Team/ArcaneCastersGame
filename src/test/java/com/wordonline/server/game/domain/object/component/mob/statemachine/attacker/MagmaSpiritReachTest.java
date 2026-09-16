package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.CombatRange;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The magma spirit summons its fist instead of swinging at the target itself, so the summon point
 * is the attack. It used to be clamped to the bare attack range from the spirit's centre while the
 * range check that started the attack measured from the spirit's collider edge, which put the fist
 * inside the spirit's own body and one radius short of anything it engaged.
 */
class MagmaSpiritReachTest {

    private static final float SUMMONER_RADIUS = 1.5f;
    private static final float ATTACK_RANGE = 1.5f;
    private static final float FIST_ATTACK_RANGE = 1.0f;

    @ParameterizedTest(name = "target radius {0}")
    @ValueSource(floats = {0.5f, 1.5f})
    @DisplayName("the summoned fist covers the target the spirit spent its attack interval on")
    void summonedFistReachesTheTargetTheSummonerEngaged(float targetRadius) {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(3.5f);

        GameObject summonerObject = new GameObject(
                Master.LeftPlayer, PrefabType.MagmaSpirit, new Vector3(0f, 0f, 0f), gameContext);
        summonerObject.addCollider(new CircleCollider(summonerObject, SUMMONER_RADIUS, false));

        // the furthest the spirit ever attacks from: MoveState hands over to AttackState at an edge
        // distance of attackRange - 0.1, and AttackState never closes in
        float centerDistance = SUMMONER_RADIUS + targetRadius + (ATTACK_RANGE - 0.1f);
        GameObject target = new GameObject(
                Master.RightPlayer, PrefabType.FireSlime, new Vector3(centerDistance, 0f, 0f), gameContext);
        target.addCollider(new CircleCollider(target, targetRadius, false));

        clearInvocations(gameContext);

        SummonerMob summonerMob = new SummonerMob(
                summonerObject, 250, 0.5f, 1, 3f, ATTACK_RANGE, PrefabType.MagmaFist);
        summonerMob.target = target;
        summonerMob.targetRadius = targetRadius;
        summonerMob.setState(summonerMob.new AttackState());

        assertThat(CombatRange.contains(summonerObject, target, ATTACK_RANGE))
                .as("the spirit considers the target in range and spends its attack interval on it")
                .isTrue();

        summonerMob.update();

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        GameObject fist = created.getValue();
        assertThat(fist.getType()).isEqualTo(PrefabType.MagmaFist);
        assertThat(fist.getMaster()).isEqualTo(Master.LeftPlayer);

        // the fist carries only a trigger collider, so its own reach starts at its centre
        double gapToTargetEdge = fist.getPosition().distance(target.getPosition()) - targetRadius;
        assertThat(gapToTargetEdge)
                .as("the fist has to cover the target it was summoned at")
                .isLessThanOrEqualTo(FIST_ATTACK_RANGE);
    }

    @Test
    @DisplayName("a target too wide to stand in range is met at the summoner's edge plus its range")
    void summonPointIsClampedToTheEdgeOfTheSummonersReach() {
        float targetRadius = 1.5f;
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(3.5f);

        GameObject summonerObject = new GameObject(
                Master.LeftPlayer, PrefabType.MagmaSpirit, new Vector3(2f, 0f, 3f), gameContext);
        summonerObject.addCollider(new CircleCollider(summonerObject, SUMMONER_RADIUS, false));

        // the target's centre sits past the summon clamp while its edge is still inside the range
        float centerDistance = SUMMONER_RADIUS + targetRadius + ATTACK_RANGE;
        GameObject target = new GameObject(
                Master.RightPlayer, PrefabType.RockGolem, new Vector3(2f + centerDistance, 0f, 3f), gameContext);
        target.addCollider(new CircleCollider(target, targetRadius, false));

        clearInvocations(gameContext);

        SummonerMob summonerMob = new SummonerMob(
                summonerObject, 250, 0.5f, 1, 3f, ATTACK_RANGE, PrefabType.MagmaFist);
        summonerMob.target = target;
        summonerMob.targetRadius = targetRadius;
        summonerMob.setState(summonerMob.new AttackState());

        summonerMob.update();

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        GameObject fist = created.getValue();

        assertThat(fist.getPosition().distance(summonerObject.getPosition()))
                .as("clamped to the summoner's collider edge plus its attack range")
                .isCloseTo(SUMMONER_RADIUS + ATTACK_RANGE, Offset.offset(1e-4));
        assertThat(fist.getPosition().distance(target.getPosition()) - targetRadius)
                .as("which is exactly where the target's edge is")
                .isLessThanOrEqualTo(0.0001d);
    }
}
