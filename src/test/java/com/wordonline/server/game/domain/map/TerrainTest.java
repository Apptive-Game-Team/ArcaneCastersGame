package com.wordonline.server.game.domain.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
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
    void aPositionOnLandIsNotMoved() {
        Vector3 onBridge = new Vector3(8.5f, 0f, 2.5f);

        assertThat(RIVER.nearestLand(onBridge)).isSameAs(onBridge);
    }

    @Test
    void aPositionInTheWaterLeavesByTheNearestBank() {
        Vector3 nearTheLeftBank = RIVER.nearestLand(new Vector3(8.2f, 0f, 5.5f));
        Vector3 nearTheRightBank = RIVER.nearestLand(new Vector3(9.8f, 1f, 5.5f));

        assertThat(nearTheLeftBank.getX()).isCloseTo(8f - Terrain.LAND_MARGIN, within(1e-5f));
        assertThat(nearTheLeftBank.getZ()).isEqualTo(5.5f);
        assertThat(nearTheRightBank.getX()).isCloseTo(10f + Terrain.LAND_MARGIN, within(1e-5f));
        assertThat(nearTheRightBank.getY()).isEqualTo(1f);
        assertThat(RIVER.isWaterAt(nearTheLeftBank)).isFalse();
        assertThat(RIVER.isWaterAt(nearTheRightBank)).isFalse();
    }

    @Test
    void aPositionInTheWaterBesideABridgeCanLeaveOntoTheBridge() {
        // 행 4 의 물 칸 위쪽 가장자리 (z = 4.05)는 은행보다 다리(행 3)가 훨씬 가깝다
        Vector3 moved = RIVER.nearestLand(new Vector3(8.6f, 0f, 4.05f));

        assertThat(moved.getZ()).isCloseTo(4f - Terrain.LAND_MARGIN, within(1e-5f));
        assertThat(moved.getX()).isEqualTo(8.6f);
        assertThat(RIVER.isWaterAt(moved)).isFalse();
    }

    @Test
    void everyWaterCellOffersALandPositionOutside() {
        for (Terrain.Cell cell : RIVER.waterCells()) {
            Vector3 moved = RIVER.nearestLand(cell.center());

            assertThat(RIVER.isWaterAt(moved)).as("%s", cell).isFalse();
            assertThat(moved.getX()).isBetween(0f, (float) GameConfig.WIDTH);
            assertThat(moved.getZ()).isBetween(0f, (float) GameConfig.HEIGHT);
        }
    }

    private static Set<Terrain.Cell> concat() {
        Set<Terrain.Cell> all = new HashSet<>(RIVER.waterCells());
        all.addAll(RIVER.bridgeCells());
        return all;
    }
}
