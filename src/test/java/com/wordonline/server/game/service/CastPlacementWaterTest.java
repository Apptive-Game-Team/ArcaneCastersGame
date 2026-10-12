package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

class CastPlacementWaterTest {

    // 땅 위 소환 prefab(GroundTower)의 반경 1 이면 물에서 0.6 안쪽이 막힌다
    private static final float RADIUS = 1f;
    private static final float THRESHOLD = RADIUS * CastPlacement.PLACEMENT_OVERLAP_RATIO;

    @Test
    void aimInsideAWaterCellBlocksAGroundSummon() {
        GameContext gameContext = context(Terrain.RIVER);

        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(9f, 0f, 5f))).isTrue();
        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(8.5f, 0f, 0.5f))).isTrue();
    }

    @Test
    void theBlockEdgeIsTheNearestPointOfTheSquareAtTheOverlapThreshold() {
        GameContext gameContext = context(Terrain.RIVER);
        Magic tower = summoning(false);

        // 왼쪽 은행에서 물 칸의 왼쪽 변 x = 8 까지의 거리
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(8f - THRESHOLD + 0.01f, 0f, 5f))).isTrue();
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(8f - THRESHOLD - 0.01f, 0f, 5f))).isFalse();
        // 모서리는 변이 아니라 점까지 잰다: (7.6, 3.6)에서 칸 (8, 4)의 모서리 (8, 4)까지 약 0.566
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(7.6f, 0f, 3.6f))).isTrue();
        assertThat(CastPlacement.isBlocked(gameContext, tower, new Vector3(7.3f, 0f, 3.3f))).isFalse();
    }

    @Test
    void aBridgeCellIsNotBlocked() {
        GameContext gameContext = context(Terrain.RIVER);

        // 다리 한가운데는 가장 가까운 물 칸까지 1.5, 임계값 0.6 보다 멀다
        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(8.5f, 0f, 2.5f))).isFalse();
        assertThat(CastPlacement.isBlocked(gameContext, summoning(false), new Vector3(9.5f, 0f, 7.5f))).isFalse();
    }

    @Test
    void anAirborneSummonAndAMagicThatLeavesNoBodyAreNeverBlockedByWater() {
        GameContext gameContext = context(Terrain.RIVER);
        Vector3 inTheWater = new Vector3(9f, 0f, 5f);

        assertThat(CastPlacement.isBlocked(gameContext, summoning(true), inTheWater)).isFalse();
        assertThat(CastPlacement.isBlocked(gameContext, mock(Magic.class), inTheWater)).isFalse();
    }

    @Test
    void theOpenArenaNeverBlocksOnWater() {
        assertThat(CastPlacement.isBlocked(context(Terrain.NONE), summoning(false), new Vector3(9f, 0f, 5f))).isFalse();
    }

    @Test
    void resolveSpotMovesAGroundSummonOutOfTheWaterToAFreeSpot() {
        GameContext gameContext = context(Terrain.RIVER);
        Magic tower = summoning(false);
        Vector3 aim = new Vector3(9f, 0f, 5f);

        Optional<Vector3> spot = CastPlacement.resolveSpot(gameContext, tower, aim, new Vector3(1f, 0f, 5f), 20);

        assertThat(spot).isPresent();
        assertThat(spot.get()).isNotEqualTo(aim);
        assertThat(CastPlacement.isBlocked(gameContext, tower, spot.get())).isFalse();
        assertThat(Terrain.RIVER.isWaterAt(spot.get())).isFalse();
    }

    @Test
    void resolveSpotKeepsAFreeAimPointOnABridge() {
        GameContext gameContext = context(Terrain.RIVER);
        Vector3 aim = new Vector3(8.5f, 0f, 2.5f);

        assertThat(CastPlacement.resolveSpot(gameContext, summoning(false), aim, new Vector3(1f, 0f, 5f), 20))
                .containsSame(aim);
    }

    @Test
    void anAirborneSummonAimedAtTheWaterIsKeptWhereItWasAimed() {
        GameContext gameContext = context(Terrain.RIVER);
        Vector3 aim = new Vector3(9f, 0f, 5f);

        assertThat(CastPlacement.resolveSpot(gameContext, summoning(true), aim, new Vector3(1f, 0f, 5f), 20))
                .containsSame(aim);
    }

    private Magic summoning(boolean airborne) {
        Magic magic = mock(Magic.class, withSettings().extraInterfaces(ObjectSummoningMagic.class));
        ObjectSummoningMagic summoning = (ObjectSummoningMagic) magic;
        when(summoning.summonedPrefab()).thenReturn(PrefabType.GroundTower);
        when(summoning.summonsAirborne()).thenReturn(airborne);
        return magic;
    }

    private GameContext context(Terrain terrain) {
        GameContext gameContext = mock(GameContext.class);
        Parameters parameters = mock(Parameters.class);
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.getValueOrDefault(eq("ground_tower"), eq("radius"), anyDouble())).thenReturn((double) RADIUS);
        when(gameContext.getActiveGameObjects()).thenReturn(List.of());
        when(gameContext.getTerrain()).thenReturn(terrain);
        return gameContext;
    }
}
