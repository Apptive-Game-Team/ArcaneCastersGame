package com.wordonline.server.game.domain.object.component.mob.pathfinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;

/**
 * Finds ground paths around {@link StaticObstacle} objects, one per game session.
 * <p>
 * The live objects are scanned for obstacles at most once per frame and the grid is rebuilt only when
 * that list differs from the last one, and a field is computed per
 * goal cell the first time a walker asks for it. Mobs chase different targets but the arena has
 * only {@code columns * rows} cells, so the cache is bounded by that and cleared whenever the set
 * of blocked cells changes.
 * <p>
 * The path it returns is the walk down the field, pulled taut: waypoints that a straight segment
 * can skip are dropped, so with nothing in the way the path is just the start and the end.
 */
public class FlowFieldNavigation implements PathFinder {

    /** Roughly the body radius of a ground mob; keeps its path off the very edge of an obstacle. */
    static final float OBSTACLE_CLEARANCE = 0.5f;

    private final IntSupplier frameNumber;
    private final Supplier<List<GameObject>> gameObjects;
    private final SimplePathFinder straightLine = new SimplePathFinder();
    private final Map<Integer, FlowField> fieldsByGoalCell = new HashMap<>();

    private NavigationGrid grid = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT, List.of(), OBSTACLE_CLEARANCE);
    private List<NavigationGrid.Obstacle> obstacles = List.of();
    private int lastRefreshedFrame = -1;
    private int gridBuilds = 0;

    public FlowFieldNavigation(IntSupplier frameNumber, Supplier<List<GameObject>> gameObjects) {
        this.frameNumber = frameNumber;
        this.gameObjects = gameObjects;
    }

    @Override
    public List<Vector3> findPath(Vector3 startPosition, Vector3 endPosition) {
        refreshObstacles();

        int goalCell = grid.cellOf(endPosition);
        FlowField field = fieldsByGoalCell.computeIfAbsent(goalCell, cell -> FlowField.toward(grid, cell));
        int entryCell = entryCell(field, grid.cellOf(startPosition));
        if (entryCell == FlowField.NO_CELL) {
            // Walled off from the goal: nothing better than pushing toward it, as before.
            return straightLine.findPath(startPosition, endPosition);
        }

        return pullTaut(walkDownField(field, startPosition, entryCell, endPosition));
    }

    /**
     * A walker already standing in a blocked cell (inside an obstacle's clearance) has no cost of
     * its own, so it enters the field through its cheapest neighbor.
     */
    private int entryCell(FlowField field, int startCell) {
        if (!grid.isBlocked(startCell)) {
            return field.reaches(startCell) ? startCell : FlowField.NO_CELL;
        }

        int best = FlowField.NO_CELL;
        for (int columnStep = -1; columnStep <= 1; columnStep++) {
            for (int rowStep = -1; rowStep <= 1; rowStep++) {
                int column = grid.column(startCell) + columnStep;
                int row = grid.row(startCell) + rowStep;
                if (!grid.isWalkable(column, row)) {
                    continue;
                }
                int neighbor = row * grid.columns() + column;
                if (field.reaches(neighbor) && (best == FlowField.NO_CELL || field.costFrom(neighbor) < field.costFrom(best))) {
                    best = neighbor;
                }
            }
        }
        return best;
    }

    private List<Vector3> walkDownField(FlowField field, Vector3 startPosition, int entryCell, Vector3 endPosition) {
        List<Vector3> waypoints = new ArrayList<>();
        waypoints.add(startPosition);
        for (int cell = entryCell; field.nextCell(cell) != FlowField.NO_CELL; cell = field.nextCell(cell)) {
            waypoints.add(grid.centerOf(cell));
        }
        // The walk stops on the goal cell, whose center is replaced by the exact end position.
        waypoints.add(endPosition.grounded());
        return waypoints;
    }

    private List<Vector3> pullTaut(List<Vector3> waypoints) {
        List<Vector3> path = new ArrayList<>();
        int anchor = 0;
        path.add(waypoints.get(anchor));
        while (anchor < waypoints.size() - 1) {
            int furthestVisible = anchor + 1;
            for (int candidate = waypoints.size() - 1; candidate > anchor + 1; candidate--) {
                if (grid.isSegmentClear(waypoints.get(anchor), waypoints.get(candidate))) {
                    furthestVisible = candidate;
                    break;
                }
            }
            path.add(waypoints.get(furthestVisible));
            anchor = furthestVisible;
        }
        return path;
    }

    private void refreshObstacles() {
        int frame = frameNumber.getAsInt();
        if (frame == lastRefreshedFrame) {
            return;
        }
        lastRefreshedFrame = frame;

        List<NavigationGrid.Obstacle> latestObstacles = collectObstacles();
        // The scan above is all a frame costs while nothing changed; the grid is the expensive part.
        if (latestObstacles.equals(obstacles)) {
            return;
        }
        obstacles = latestObstacles;

        NavigationGrid latest = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT, latestObstacles, OBSTACLE_CLEARANCE);
        gridBuilds++;
        if (!latest.hasSameBlockingAs(grid)) {
            grid = latest;
            fieldsByGoalCell.clear();
        }
    }

    private List<NavigationGrid.Obstacle> collectObstacles() {
        List<NavigationGrid.Obstacle> found = new ArrayList<>();
        for (GameObject gameObject : gameObjects.get()) {
            if (!gameObject.isActive() || !gameObject.hasComponent(StaticObstacle.class)) {
                continue;
            }
            // The position is copied: the object keeps the Vector3 it returns, so comparing against
            // that same instance later would call a moved obstacle unchanged.
            gameObject.getFirstCircleCollider(false)
                    .map(CircleCollider::getRadius)
                    .ifPresent(radius -> found.add(new NavigationGrid.Obstacle(new Vector3(gameObject.getPosition()), radius)));
        }
        return found;
    }

    /** How many times the grid was built from the live objects; the tests use it to see a skipped rebuild. */
    int gridBuilds() {
        return gridBuilds;
    }
}
