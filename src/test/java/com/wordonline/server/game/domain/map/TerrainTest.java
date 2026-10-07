package com.wordonline.server.game.domain.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.Vector3;

class TerrainTest {

    private static final Terrain RIVER = Terrain.RIVER;

    @Test
    void theRiverHasEightWaterCellsAndTwelveBridgeCells() {
        assertThat(RIVER.waterCells()).hasSize(8);
        assertThat(RIVER.bridgeCells()).hasSize(12);
    }

    @Test
    void waterIsColumnsEightAndNineOnRowsZeroFourFiveAndNine() {
        Set<Terrain.Cell> expected = new HashSet<>();
        for (int column : new int[] {8, 9}) {
            for (int row : new int[] {0, 4, 5, 9}) {
                expected.add(new Terrain.Cell(column, row));
            }
        }

        assertThat(new HashSet<>(RIVER.waterCells())).isEqualTo(expected);
    }

    @Test
    void bridgesAreColumnsEightAndNineOnRowsOneToThreeAndSixToEight() {
        Set<Terrain.Cell> expected = new HashSet<>();
        for (int column : new int[] {8, 9}) {
            for (int row : new int[] {1, 2, 3, 6, 7, 8}) {
                expected.add(new Terrain.Cell(column, row));
            }
        }

        assertThat(new HashSet<>(RIVER.bridgeCells())).isEqualTo(expected);
    }

    @Test
    void waterAndBridgeCellsDoNotShareACell() {
        Set<Terrain.Cell> shared = new HashSet<>(RIVER.waterCells());
        shared.retainAll(RIVER.bridgeCells());

        assertThat(shared).isEmpty();
    }

    @Test
    void everyCellIsInsideTheArena() {
        for (Terrain.Cell cell : concat()) {
            assertThat(cell.column()).isBetween(0, GameConfig.WIDTH - 1);
            assertThat(cell.row()).isBetween(0, GameConfig.HEIGHT - 1);
        }
    }

    @Test
    void theLayoutIsMirroredAboutXEqualsNine() {
        for (Terrain.Cell cell : RIVER.waterCells()) {
            assertThat(RIVER.waterCells()).contains(new Terrain.Cell(GameConfig.WIDTH - 1 - cell.column(), cell.row()));
        }
        for (Terrain.Cell cell : RIVER.bridgeCells()) {
            assertThat(RIVER.bridgeCells()).contains(new Terrain.Cell(GameConfig.WIDTH - 1 - cell.column(), cell.row()));
        }
    }

    @Test
    void theOpenArenaHasNoWater() {
        assertThat(Terrain.NONE.isEmpty()).isTrue();
        assertThat(Terrain.NONE.isWater(8, 0)).isFalse();
        assertThat(Terrain.NONE.distanceToWater(new Vector3(9f, 0f, 0.5f))).isInfinite();
    }

    @Test
    void aCellCenterTellsWaterFromBridge() {
        assertThat(RIVER.isWaterAt(new Vector3(8.5f, 0f, 0.5f))).isTrue();
        assertThat(RIVER.isWaterAt(new Vector3(9.5f, 3f, 4.5f))).isTrue();
        assertThat(RIVER.isWaterAt(new Vector3(8.5f, 0f, 2.5f))).isFalse();
        assertThat(RIVER.isWaterAt(new Vector3(7.99f, 0f, 5f))).isFalse();
        assertThat(RIVER.isWaterAt(new Vector3(10.01f, 0f, 5f))).isFalse();
    }

    @Test
    void distanceToWaterIsZeroInsideACellAndMeasuredToTheNearestPointOfTheSquare() {
        assertThat(RIVER.distanceToWater(new Vector3(9f, 0f, 5f))).isZero();
        assertThat(RIVER.distanceToWater(new Vector3(7f, 0f, 5f))).isCloseTo(1.0, within(1e-6));
        // 다리 한가운데 (8.5, 2.5)는 행 4 의 물 칸 아래 모서리 z = 4 까지 1.5
        assertThat(RIVER.distanceToWater(new Vector3(8.5f, 0f, 2.5f))).isCloseTo(1.5, within(1e-6));
        // 물 칸 (8, 4)의 모서리 (8, 4)에서 대각선으로 (7, 3)까지
        assertThat(RIVER.distanceToWater(new Vector3(7f, 0f, 3f))).isCloseTo(Math.sqrt(2), within(1e-6));
    }

    @Test
    void aWaterCellHasSidesOnlyTowardLandAndBridges() {
        // 행 0 의 왼쪽 칸: 동쪽은 물, 남쪽은 맵 경계, 서쪽은 땅, 북쪽은 다리
        assertThat(RIVER.exposedSides(new Terrain.Cell(8, 0))).containsExactlyInAnyOrder(
                new Terrain.Side(new Vector3(-0.5f, 0f, 0.5f), new Vector3(-0.5f, 0f, -0.5f)),
                new Terrain.Side(new Vector3(0.5f, 0f, 0.5f), new Vector3(-0.5f, 0f, 0.5f)));
        // 행 4 와 5 사이는 물끼리 맞닿아 변이 없다
        assertThat(RIVER.exposedSides(new Terrain.Cell(8, 4))).hasSize(2);
        assertThat(RIVER.exposedSides(new Terrain.Cell(9, 5))).hasSize(2);
        assertThat(RIVER.exposedSides(new Terrain.Cell(8, 2))).isEmpty();
        assertThat(RIVER.exposedSides(new Terrain.Cell(3, 3))).isEmpty();
        assertThat(Terrain.NONE.exposedSides(new Terrain.Cell(8, 0))).isEmpty();
    }

    @Test
    void theSidesAreSixteenSegmentsAndEveryEndIsSharedOrOnTheArenaBoundary() {
        List<float[]> ends = new ArrayList<>();
        int sides = 0;
        for (Terrain.Cell cell : RIVER.waterCells()) {
            for (Terrain.Side side : RIVER.exposedSides(cell)) {
                sides++;
                Vector3 center = cell.center();
                ends.add(new float[] {center.getX() + side.start().getX(), center.getZ() + side.start().getZ()});
                ends.add(new float[] {center.getX() + side.end().getX(), center.getZ() + side.end().getZ()});
            }
        }

        assertThat(sides).isEqualTo(16);
        for (float[] end : ends) {
            boolean onBoundary = end[0] == 0f || end[0] == GameConfig.WIDTH || end[1] == 0f || end[1] == GameConfig.HEIGHT;
            long sharing = ends.stream().filter(other -> other[0] == end[0] && other[1] == end[1]).count();
            // a free end would be a gap a ground body could walk through
            assertThat(onBoundary || sharing == 2).as("end (%s, %s)", end[0], end[1]).isTrue();
        }
    }

    private static Set<Terrain.Cell> concat() {
        Set<Terrain.Cell> all = new HashSet<>(RIVER.waterCells());
        all.addAll(RIVER.bridgeCells());
        return all;
    }
}
