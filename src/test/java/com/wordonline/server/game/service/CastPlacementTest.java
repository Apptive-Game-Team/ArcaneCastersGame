package com.wordonline.server.game.service;

import java.util.List;
import java.util.Optional;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CastPlacementTest {

    @Test
    void overlapThresholdIsTheRadiusSumTimesTheOverlapRatio() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        Magic tower = summoning(false);
        float sum = 1.5f; // 타워 반경 1 + 몸 반경 0.5

        assertThat(CastPlacement.PLACEMENT_OVERLAP_RATIO).isEqualTo(0.6f);
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(5f + 0.7f * sum, 0f, 5f))).isFalse();
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(5f + 0.5f * sum, 0f, 5f))).isTrue();
    }

    @Test
    void airborneBodiesNeitherBlockNorAreBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 3f, 5f), 0.5f)));

        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(5f, 0f, 5f))).isFalse();

        GameContext crowded = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        assertThat(CastPlacement.isBlocked(crowded, summoning(true), new Vector3(5f, 0f, 5f))).isFalse();
    }

    @Test
    void magicsThatLeaveNoBodyAreNeverBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));

        assertThat(CastPlacement.isBlocked(gameContext, mock(Magic.class), new Vector3(5f, 0f, 5f))).isFalse();
    }

    @Test
    void freeAimPointIsReturnedUnchanged() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        Vector3 aim = new Vector3(9f, 0f, 5f);

        Optional<Vector3> spot = CastPlacement.resolveSpot(
                gameContext, summoning(false), aim, new Vector3(1f, 0f, 5f), 20);

        assertThat(spot).containsSame(aim);
    }

    @Test
    void blockedAimSnapsToTheFirstFreeDirectionOfTheInnermostFreeRing() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));

        Optional<Vector3> spot = CastPlacement.resolveSpot(
                gameContext, summoning(false), new Vector3(5f, 0f, 5f), new Vector3(1f, 0f, 5f), 9);

        // 임계값 0.9 이므로 링 1~3 (0.25~0.75)은 막히고, 링 4 (1.0)의 각도 0 (+x)이 처음 빈자리다
        assertThat(spot).isPresent();
        assertThat(spot.get().getX()).isCloseTo(6f, within(1e-4f));
        assertThat(spot.get().getZ()).isCloseTo(5f, within(1e-4f));
    }

    @Test
    void directionsAreTriedInOrderSkippingCandidatesOutsideTheMap() {
        GameContext gameContext = context(List.of(body(new Vector3(18f, 0f, 5f), 0.5f)));

        Optional<Vector3> spot = CastPlacement.resolveSpot(
                gameContext, summoning(false), new Vector3(18f, 0f, 5f), new Vector3(15f, 0f, 5f), 9);

        // 링 4 에서 각도 0~3 은 x > 18 이라 맵 밖이고, 각도 90도 (+z)가 처음 맵 안의 빈자리다
        assertThat(spot).isPresent();
        assertThat(spot.get().getX()).isCloseTo(18f, within(1e-4f));
        assertThat(spot.get().getZ()).isCloseTo(6f, within(1e-4f));
    }

    @Test
    void candidatesBeyondTheRangeAreSkipped() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 0.5f)));
        Magic tower = summoning(false);
        Vector3 aim = new Vector3(5f, 0f, 5f);

        assertThat(CastPlacement.resolveSpot(gameContext, tower, aim, aim, 0.9)).isEmpty();
        assertThat(CastPlacement.resolveSpot(gameContext, tower, aim, aim, 1.05)).isPresent();
    }

    @Test
    void emptyWhenEverythingWithinRangeIsBlocked() {
        GameContext gameContext = context(List.of(body(new Vector3(5f, 0f, 5f), 5f)));
        Vector3 aim = new Vector3(5f, 0f, 5f);

        // 임계값 (5 + 1) * 0.6 = 3.6 이 탐색 반경 최대 3.0 보다 크다
        assertThat(CastPlacement.resolveSpot(gameContext, summoning(false), aim, aim, 3)).isEmpty();
    }

    private Magic summoning(boolean airborne) {
        Magic magic = mock(Magic.class, withSettings().extraInterfaces(ObjectSummoningMagic.class));
        ObjectSummoningMagic summoning = (ObjectSummoningMagic) magic;
        when(summoning.summonedPrefab()).thenReturn(PrefabType.GroundTower);
        when(summoning.summonsAirborne()).thenReturn(airborne);
        return magic;
    }

    private GameContext context(List<GameObject> objects) {
        GameContext gameContext = mock(GameContext.class);
        Parameters parameters = mock(Parameters.class);
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.getValueOrDefault(eq("ground_tower"), eq("radius"), anyDouble())).thenReturn(1d);
        when(gameContext.getActiveGameObjects()).thenReturn(objects);
        return gameContext;
    }

    private GameObject body(Vector3 position, float radius) {
        GameObject gameObject = mock(GameObject.class);
        CircleCollider collider = mock(CircleCollider.class);
        when(collider.getRadius()).thenReturn(radius);
        when(gameObject.isActive()).thenReturn(true);
        when(gameObject.getPosition()).thenReturn(position);
        when(gameObject.getComponents(Damageable.class)).thenReturn(List.of(mock(Damageable.class)));
        when(gameObject.getFirstCircleCollider(false)).thenReturn(Optional.of(collider));
        return gameObject;
    }
}
