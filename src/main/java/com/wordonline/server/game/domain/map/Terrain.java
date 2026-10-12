package com.wordonline.server.game.domain.map;

import java.util.ArrayList;
import java.util.List;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * Which cells of the arena are water. A cell is a square of side 1: column is floor(x), row is
 * floor(z). Pathfinding, the summon placement check and the water colliders all ask this one
 * object, so the three can never disagree about where the water is.
 * <p>
 * Water blocks ground bodies only. {@link #exposedSides} gives the cell sides that become
 * colliders. Bridge cells are ordinary land that exist to be drawn: they are kept here so the
 * loop can spawn them and the tests can pin the layout.
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

    /** A segment from {@code start} to {@code end}, relative to the center of the cell it belongs to. */
    public record Side(Vector3 start, Vector3 end) {
    }

    /**
     * The sides of a water cell that face something that is not water: land beyond the river or a
     * bridge cell. A side shared with another water cell is left out, and so is a side on the arena
     * boundary, which the map wall already covers. The sides are given relative to the cell center
     * (the position of the object that carries them), counter-clockwise from the east side, and the
     * four sides of one cell meet at its corners, so two neighbouring cells leave no gap between
     * their sides. A cell that is not water has no sides.
     */
    public List<Side> exposedSides(Cell cell) {
        List<Side> sides = new ArrayList<>();
        if (!isWater(cell.column(), cell.row())) {
            return sides;
        }
        float half = 0.5f;
        // east, north (+z), west, south (-z)
        int[][] neighbours = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        Vector3[][] ends = {
                {new Vector3(half, 0f, -half), new Vector3(half, 0f, half)},
                {new Vector3(half, 0f, half), new Vector3(-half, 0f, half)},
                {new Vector3(-half, 0f, half), new Vector3(-half, 0f, -half)},
                {new Vector3(-half, 0f, -half), new Vector3(half, 0f, -half)},
        };
        for (int i = 0; i < neighbours.length; i++) {
            int column = cell.column() + neighbours[i][0];
            int row = cell.row() + neighbours[i][1];
            boolean insideArena = column >= 0 && column < GameConfig.WIDTH && row >= 0 && row < GameConfig.HEIGHT;
            if (insideArena && !isWater(column, row)) {
                sides.add(new Side(ends[i][0], ends[i][1]));
            }
        }
        return sides;
    }
}
