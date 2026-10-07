package com.wordonline.server.game.domain.object.component.mob.pathfinder;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * For one goal cell, the cost of reaching it from every cell and the neighbor to step to next.
 * <p>
 * It is computed once by spreading outward from the goal (Dijkstra), so any number of walkers
 * heading for the same goal read their direction from the same field instead of each running a
 * search of their own.
 */
final class FlowField {

    static final int NO_CELL = -1;
    private static final float STRAIGHT_COST = 1f;
    private static final float DIAGONAL_COST = (float) Math.sqrt(2);

    private final float[] costToGoal;
    private final int[] nextCell;

    private FlowField(float[] costToGoal, int[] nextCell) {
        this.costToGoal = costToGoal;
        this.nextCell = nextCell;
    }

    /**
     * The goal cell is a seed even when it is blocked: a target standing next to an obstacle sits
     * inside the obstacle's clearance, and the walkers still have to be able to approach it.
     */
    static FlowField toward(NavigationGrid grid, int goalCell) {
        float[] costToGoal = new float[grid.cellCount()];
        int[] nextCell = new int[grid.cellCount()];
        Arrays.fill(costToGoal, Float.POSITIVE_INFINITY);
        Arrays.fill(nextCell, NO_CELL);

        PriorityQueue<Frontier> frontier = new PriorityQueue<>((a, b) -> Float.compare(a.cost, b.cost));
        costToGoal[goalCell] = 0f;
        frontier.add(new Frontier(goalCell, 0f));

        while (!frontier.isEmpty()) {
            Frontier current = frontier.poll();
            if (current.cost > costToGoal[current.cell]) {
                continue;
            }
            spread(grid, current, costToGoal, nextCell, frontier);
        }
        return new FlowField(costToGoal, nextCell);
    }

    private static void spread(NavigationGrid grid, Frontier current, float[] costToGoal, int[] nextCell,
                               PriorityQueue<Frontier> frontier) {
        int column = grid.column(current.cell);
        int row = grid.row(current.cell);
        for (int columnStep = -1; columnStep <= 1; columnStep++) {
            for (int rowStep = -1; rowStep <= 1; rowStep++) {
                if (columnStep == 0 && rowStep == 0) {
                    continue;
                }
                if (!canStep(grid, column, row, columnStep, rowStep)) {
                    continue;
                }
                int neighbor = (row + rowStep) * grid.columns() + column + columnStep;
                float stepCost = columnStep != 0 && rowStep != 0 ? DIAGONAL_COST : STRAIGHT_COST;
                float cost = current.cost + stepCost;
                if (cost < costToGoal[neighbor]) {
                    costToGoal[neighbor] = cost;
                    nextCell[neighbor] = current.cell;
                    frontier.add(new Frontier(neighbor, cost));
                }
            }
        }
    }

    /** A diagonal step may not cut the corner of a blocked cell. */
    private static boolean canStep(NavigationGrid grid, int column, int row, int columnStep, int rowStep) {
        if (!grid.isWalkable(column + columnStep, row + rowStep)) {
            return false;
        }
        if (columnStep == 0 || rowStep == 0) {
            return true;
        }
        return grid.isWalkable(column + columnStep, row) && grid.isWalkable(column, row + rowStep);
    }

    boolean reaches(int cell) {
        return costToGoal[cell] < Float.POSITIVE_INFINITY;
    }

    float costFrom(int cell) {
        return costToGoal[cell];
    }

    /** The neighbor one step closer to the goal, or {@link #NO_CELL} at the goal and at cells that cannot reach it. */
    int nextCell(int cell) {
        return nextCell[cell];
    }

    private record Frontier(int cell, float cost) {
    }
}
