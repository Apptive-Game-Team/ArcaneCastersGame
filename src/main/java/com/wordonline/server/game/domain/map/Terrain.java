package com.wordonline.server.game.domain.map;

import java.util.ArrayList;
import java.util.List;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * Which cells of the arena are water. A cell is a square of side 1: column is floor(x), row is
 * floor(z). Pathfinding, the summon placement check and the physics step all ask this one object,
 * so the three can never disagree about where the water is.
 * <p>
 * Water blocks ground bodies only. Bridge cells are ordinary land that exist to be drawn: they
 * are kept here so the loop can spawn them and the tests can pin the layout.
 */
public final class Terrain {

    public record Cell(int column, int row) {

        public Vector3 center() {
            return new Vector3(column + 0.5f, 0f, row + 0.5f);
        }
    }

    /** The open arena: no water, nothing to draw. */
    public static final Terrain NONE = new Terrain(List.of(), List.of());

    /**
     * The river layout. It fills columns 8 and 9 (x from 8 to 10). Bridges cross it on rows 1 to 3
     * and rows 6 to 8, each 3 wide, and the other rows (0, 4, 5, 9) are water: 8 water cells and
     * 12 bridge cells, mirrored about x = 9.
     */
    public static final Terrain RIVER = river();

    // How far inside the land cell a body is put when it is moved out of the water, so that the
    // position is unambiguously in the land cell and not on the shared edge.
    static final float LAND_MARGIN = 0.01f;

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final List<Cell> waterCells;
    private final List<Cell> bridgeCells;
    private final boolean[] water = new boolean[GameConfig.WIDTH * GameConfig.HEIGHT];

    private Terrain(List<Cell> waterCells, List<Cell> bridgeCells) {
        this.waterCells = List.copyOf(waterCells);
        this.bridgeCells = List.copyOf(bridgeCells);
        for (Cell cell : waterCells) {
            water[cell.row() * GameConfig.WIDTH + cell.column()] = true;
        }
    }

    private static Terrain river() {
        int[] riverColumns = {8, 9};
        int[] bridgeRows = {1, 2, 3, 6, 7, 8};
        List<Cell> waterCells = new ArrayList<>();
        List<Cell> bridgeCells = new ArrayList<>();
        for (int column : riverColumns) {
            for (int row = 0; row < GameConfig.HEIGHT; row++) {
                if (contains(bridgeRows, row)) {
                    bridgeCells.add(new Cell(column, row));
                } else {
                    waterCells.add(new Cell(column, row));
                }
            }
        }
        return new Terrain(waterCells, bridgeCells);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    public List<Cell> waterCells() {
        return waterCells;
    }

    public List<Cell> bridgeCells() {
        return bridgeCells;
    }

    public boolean isEmpty() {
        return waterCells.isEmpty();
    }

    /** Whether the cell exists in the arena and is water. */
    public boolean isWater(int column, int row) {
        return column >= 0 && column < GameConfig.WIDTH && row >= 0 && row < GameConfig.HEIGHT
                && water[row * GameConfig.WIDTH + column];
    }

    /** Whether the position, by its x and z, lies in a water cell. */
    public boolean isWaterAt(Vector3 position) {
        return isWater((int) Math.floor(position.getX()), (int) Math.floor(position.getZ()));
    }

    /**
     * The distance from the position (x and z) to the nearest point of any water cell's square, or
     * infinity when there is no water. A position inside a water cell is at distance 0.
     */
    public double distanceToWater(Vector3 position) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Cell cell : waterCells) {
            double offsetX = Math.max(Math.max(cell.column() - position.getX(), position.getX() - (cell.column() + 1)), 0);
            double offsetZ = Math.max(Math.max(cell.row() - position.getZ(), position.getZ() - (cell.row() + 1)), 0);
            nearest = Math.min(nearest, Math.hypot(offsetX, offsetZ));
        }
        return nearest;
    }

    /**
     * The position itself when it is on land; otherwise the closest position on land, found by
     * leaving the water straight along one of the four axes and keeping the shortest way out.
     * Only the x and z change. A position with no way out (surrounded by water up to the arena
     * edge) is returned unchanged.
     */
    public Vector3 nearestLand(Vector3 position) {
        int column = (int) Math.floor(position.getX());
        int row = (int) Math.floor(position.getZ());
        if (!isWater(column, row)) {
            return position;
        }

        Vector3 best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int[] direction : DIRECTIONS) {
            int landColumn = column;
            int landRow = row;
            while (isWater(landColumn, landRow)) {
                landColumn += direction[0];
                landRow += direction[1];
            }
            if (landColumn < 0 || landColumn >= GameConfig.WIDTH || landRow < 0 || landRow >= GameConfig.HEIGHT) {
                continue;
            }

            float x = position.getX();
            float z = position.getZ();
            if (direction[0] > 0) {
                x = landColumn + LAND_MARGIN;
            } else if (direction[0] < 0) {
                x = landColumn + 1 - LAND_MARGIN;
            } else if (direction[1] > 0) {
                z = landRow + LAND_MARGIN;
            } else {
                z = landRow + 1 - LAND_MARGIN;
            }
            float distance = Math.abs(x - position.getX()) + Math.abs(z - position.getZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Vector3(x, position.getY(), z);
            }
        }
        return best == null ? position : best;
    }
}
