package com.wordonline.server.game.domain.object.component.mob.pathfinder;

import java.util.Arrays;
import java.util.List;

import com.wordonline.server.game.domain.object.Vector3;

/**
 * The arena cut into square cells of side {@link #CELL_SIZE}, each either walkable or blocked.
 * <p>
 * A grid never changes after construction: when an obstacle appears or disappears the caller
 * builds a new grid and compares it with {@link #hasSameBlockingAs}, which is how cached
 * flow fields learn they are stale.
 */
public final class NavigationGrid {

    public static final float CELL_SIZE = 1f;
    private static final float SEGMENT_SAMPLE_STEP = 0.25f;

    public record Obstacle(Vector3 center, float radius) {
    }

    private final int columns;
    private final int rows;
    private final boolean[] blocked;

    /**
     * @param clearance extra radius added to every obstacle so that a walker with a body of that
     *                  radius, steering along cell centers, does not scrape the obstacle
     */
    public NavigationGrid(int columns, int rows, List<Obstacle> obstacles, float clearance) {
        this.columns = columns;
        this.rows = rows;
        this.blocked = new boolean[columns * rows];
        for (Obstacle obstacle : obstacles) {
            block(obstacle, clearance);
        }
    }

    private void block(Obstacle obstacle, float clearance) {
        float reach = obstacle.radius() + clearance;
        float reachSquared = reach * reach;
        for (int cell = 0; cell < blocked.length; cell++) {
            // Plain arithmetic: a grid is rebuilt whenever the obstacle set changes, and building a
            // Vector3 per cell per obstacle dominated that cost.
            float offsetX = (column(cell) + 0.5f) * CELL_SIZE - obstacle.center().getX();
            float offsetZ = (row(cell) + 0.5f) * CELL_SIZE - obstacle.center().getZ();
            if (offsetX * offsetX + offsetZ * offsetZ <= reachSquared) {
                blocked[cell] = true;
            }
        }
    }

    public int cellCount() {
        return blocked.length;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public boolean isBlocked(int cell) {
        return blocked[cell];
    }

    public int column(int cell) {
        return cell % columns;
    }

    public int row(int cell) {
        return cell / columns;
    }

    /** Whether the cell at the column and row exists and is walkable. */
    public boolean isWalkable(int column, int row) {
        return column >= 0 && column < columns && row >= 0 && row < rows && !blocked[row * columns + column];
    }

    /** The cell holding the position; positions outside the arena belong to the nearest edge cell. */
    public int cellOf(Vector3 position) {
        int column = Math.clamp((int) Math.floor(position.getX() / CELL_SIZE), 0, columns - 1);
        int row = Math.clamp((int) Math.floor(position.getZ() / CELL_SIZE), 0, rows - 1);
        return row * columns + column;
    }

    public Vector3 centerOf(int cell) {
        return new Vector3((column(cell) + 0.5f) * CELL_SIZE, 0f, (row(cell) + 0.5f) * CELL_SIZE);
    }

    /** Whether walking straight from one position to the other never enters a blocked cell. */
    public boolean isSegmentClear(Vector3 from, Vector3 to) {
        double length = from.grounded().distance(to.grounded());
        int samples = Math.max(1, (int) Math.ceil(length / SEGMENT_SAMPLE_STEP));
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            Vector3 point = from.plus(to.subtract(from).multiply(t));
            if (blocked[cellOf(point)]) {
                return false;
            }
        }
        return true;
    }

    public boolean hasSameBlockingAs(NavigationGrid other) {
        return columns == other.columns && rows == other.rows && Arrays.equals(blocked, other.blocked);
    }
}
