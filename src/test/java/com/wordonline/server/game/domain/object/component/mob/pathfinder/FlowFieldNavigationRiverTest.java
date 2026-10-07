package com.wordonline.server.game.domain.object.component.mob.pathfinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.Vector3;


class FlowFieldNavigationRiverTest {

    private static final Vector3 LEFT_BANK = new Vector3(2f, 0f, 5f);
    private static final Vector3 RIGHT_BANK = new Vector3(16f, 0f, 5f);

    private Terrain terrain = Terrain.RIVER;
    private int frame = 0;
    private final FlowFieldNavigation navigation =
            new FlowFieldNavigation(() -> frame, List::of, () -> terrain);

    @Test
    void aGroundPathAcrossTheRiverCrossesXEqualsNineOnlyOnABridge() {
        List<Vector3> path = navigation.findPath(LEFT_BANK, RIGHT_BANK);

        assertThat(path.getFirst()).isEqualTo(LEFT_BANK);
        assertThat(path.getLast()).isEqualTo(RIGHT_BANK);
        List<Float> crossings = crossingsOfXEqualsNine(path);
        assertThat(crossings).isNotEmpty();
        assertThat(crossings).allMatch(z -> (z >= 1f && z <= 4f) || (z >= 6f && z <= 9f));
    }

    @Test
    void thePathNeverStepsIntoAWaterCell() {
        List<Vector3> path = navigation.findPath(LEFT_BANK, RIGHT_BANK);

        for (int i = 0; i + 1 < path.size(); i++) {
            for (int step = 0; step <= 40; step++) {
                Vector3 point = path.get(i).plus(path.get(i + 1).subtract(path.get(i)).multiply(step / 40f));
                assertThat(terrain.isWaterAt(point)).as("%s", point).isFalse();
            }
        }
    }

    @Test
    void theWaterCellsAreBlockedAndEveryBridgeCellIsWalkable() {
        NavigationGrid grid = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT, List.of(),
                FlowFieldNavigation.OBSTACLE_CLEARANCE, terrain);

        for (Terrain.Cell cell : terrain.waterCells()) {
            assertThat(grid.isWalkable(cell.column(), cell.row())).as("%s", cell).isFalse();
        }
        for (Terrain.Cell cell : terrain.bridgeCells()) {
            assertThat(grid.isWalkable(cell.column(), cell.row())).as("%s", cell).isTrue();
        }
    }

    @Test
    void bothBanksAreConnectedThroughEachBridgeOnItsOwn() {
        // 한 다리를 장애물로 막고, 남은 다리만으로 양쪽 은행이 이어지는지 본다
        assertThat(rowsCrossedWhenBlocking(new Vector3(9f, 0f, 7.5f))).isNotEmpty().allMatch(row -> row >= 1 && row <= 3);
        assertThat(rowsCrossedWhenBlocking(new Vector3(9f, 0f, 2.5f))).isNotEmpty().allMatch(row -> row >= 6 && row <= 8);
    }

    @Test
    void everyLandCellOfTheLeftBankReachesEveryLandCellOfTheRightBank() {
        NavigationGrid grid = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT, List.of(),
                FlowFieldNavigation.OBSTACLE_CLEARANCE, terrain);
        FlowField field = FlowField.toward(grid, grid.cellOf(RIGHT_BANK));

        for (int row = 0; row < GameConfig.HEIGHT; row++) {
            for (int column = 0; column < 8; column++) {
                assertThat(field.reaches(row * GameConfig.WIDTH + column)).as("(%d, %d)", column, row).isTrue();
            }
        }
    }

    @Test
    void aTerrainSetAfterTheNavigationWasBuiltIsPickedUp() {
        terrain = Terrain.NONE;
        assertThat(navigation.findPath(LEFT_BANK, RIGHT_BANK)).containsExactly(LEFT_BANK, RIGHT_BANK);

        terrain = Terrain.RIVER;
        frame++;

        assertThat(navigation.findPath(LEFT_BANK, RIGHT_BANK)).hasSizeGreaterThan(2);
    }

    @Test
    void anAerialMobsPathStaysStraightOverTheWater() {
        // 공중 mob 은 BehaviorMob 에서 flow field 대신 이 길찾기를 쓴다
        List<Vector3> path = new SimplePathFinder().findPath(LEFT_BANK, RIGHT_BANK);

        assertThat(path.getFirst()).isEqualTo(LEFT_BANK);
        assertThat(path.getLast()).isEqualTo(RIGHT_BANK);
        assertThat(path).allMatch(point -> point.getZ() == 5f);
        assertThat(path).extracting(Vector3::getX).isSorted();
    }

    @Test
    void aWalkerStandingInTheWaterStillFindsItsWayOut() {
        List<Vector3> path = navigation.findPath(new Vector3(8.5f, 0f, 5.5f), RIGHT_BANK);

        assertThat(path.getLast()).isEqualTo(RIGHT_BANK);
        assertThat(path).hasSizeGreaterThanOrEqualTo(2);
    }

    /** The rows of the columns 8 and 9 cells a walk from the left bank to the right bank steps on. */
    private static List<Integer> rowsCrossedWhenBlocking(Vector3 obstacleCenter) {
        NavigationGrid grid = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT,
                List.of(new NavigationGrid.Obstacle(obstacleCenter, 1.6f)),
                FlowFieldNavigation.OBSTACLE_CLEARANCE, Terrain.RIVER);
        FlowField field = FlowField.toward(grid, grid.cellOf(RIGHT_BANK));
        int cell = grid.cellOf(LEFT_BANK);
        assertThat(field.reaches(cell)).isTrue();

        java.util.ArrayList<Integer> rows = new java.util.ArrayList<>();
        for (; field.nextCell(cell) != FlowField.NO_CELL; cell = field.nextCell(cell)) {
            if (grid.column(cell) == 8 || grid.column(cell) == 9) {
                rows.add(grid.row(cell));
            }
        }
        return rows;
    }

    /** The z of every point where the path crosses x = 9. */
    private static List<Float> crossingsOfXEqualsNine(List<Vector3> path) {
        java.util.ArrayList<Float> crossings = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            Vector3 from = path.get(i);
            Vector3 to = path.get(i + 1);
            if ((from.getX() - 9f) * (to.getX() - 9f) < 0f || from.getX() == 9f) {
                float t = to.getX() == from.getX() ? 0f : (9f - from.getX()) / (to.getX() - from.getX());
                crossings.add(from.getZ() + (to.getZ() - from.getZ()) * t);
            }
        }
        return crossings;
    }
}
