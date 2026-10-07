package com.wordonline.server.game.domain.object.component.mob.pathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;

class FlowFieldNavigationTest {

    private static final Vector3 START = new Vector3(2f, 0f, 5f);
    private static final Vector3 END = new Vector3(16f, 0f, 5f);

    private final List<GameObject> gameObjects = new ArrayList<>();
    private int frame = 0;
    private FlowFieldNavigation navigation;

    @BeforeEach
    void setUp() {
        navigation = new FlowFieldNavigation(() -> frame, () -> gameObjects);
    }

    @Test
    void anOpenArenaGivesTheStraightSegmentFromStartToEnd() {
        List<Vector3> path = navigation.findPath(START, END);

        assertThat(path).containsExactly(START, END);
    }

    @Test
    void anObstacleOnTheStraightLineIsWalkedAround() {
        gameObjects.add(obstacleAt(9f, 5f, 1f));

        List<Vector3> path = navigation.findPath(START, END);

        assertThat(path).hasSizeGreaterThan(2);
        assertThat(path.getFirst()).isEqualTo(START);
        assertThat(path.getLast()).isEqualTo(END);
        assertThat(everySegmentAvoids(path, new NavigationGrid.Obstacle(new Vector3(9f, 0f, 5f), 1f))).isTrue();
    }

    @Test
    void theReturnedPathCanBeConsumedFromTheFront() {
        gameObjects.add(obstacleAt(9f, 5f, 1f));

        List<Vector3> path = navigation.findPath(START, END);

        // The mob states drop waypoints with List.remove(0) as they are reached.
        path.remove(0);
        assertThat(path).isNotEmpty();
    }

    @Test
    void aWallAcrossTheWholeArenaFallsBackToTheStraightLine() {
        for (int row = 0; row < GameConfig.HEIGHT; row++) {
            gameObjects.add(obstacleAt(9f, row + 0.5f, 0.5f));
        }

        List<Vector3> path = navigation.findPath(START, END);

        assertThat(path).isEqualTo(new SimplePathFinder().findPath(START, END));
    }

    @Test
    void aWalkerInsideAnObstaclesClearanceStillFindsItsWayOut() {
        gameObjects.add(obstacleAt(9f, 5f, 1f));
        Vector3 besideTheObstacle = new Vector3(8.3f, 0f, 5f);

        List<Vector3> path = navigation.findPath(besideTheObstacle, END);

        assertThat(path.getFirst()).isEqualTo(besideTheObstacle);
        assertThat(path.getLast()).isEqualTo(END);
        assertThat(path).hasSizeGreaterThan(2);
    }

    @Test
    void aGoalInsideAnObstaclesClearanceCanStillBeApproached() {
        gameObjects.add(obstacleAt(9f, 5f, 1f));
        Vector3 nextToTheObstacle = new Vector3(9.7f, 0f, 5f);

        List<Vector3> path = navigation.findPath(START, nextToTheObstacle);

        assertThat(path.getLast()).isEqualTo(nextToTheObstacle);
        assertThat(path).hasSizeGreaterThan(2);
    }

    @Test
    void anObstacleThatDisappearsStopsBendingThePathOnTheNextFrame() {
        GameObject rock = obstacleAt(9f, 5f, 1f);
        gameObjects.add(rock);
        assertThat(navigation.findPath(START, END)).hasSizeGreaterThan(2);

        gameObjects.remove(rock);
        frame++;

        assertThat(navigation.findPath(START, END)).containsExactly(START, END);
    }

    @Test
    void anObstacleThatAppearsBendsThePathOnTheNextFrame() {
        assertThat(navigation.findPath(START, END)).containsExactly(START, END);

        gameObjects.add(obstacleAt(9f, 5f, 1f));
        frame++;

        assertThat(navigation.findPath(START, END)).hasSizeGreaterThan(2);
    }

    @Test
    void anInactiveObstacleIsIgnored() {
        GameObject rock = obstacleAt(9f, 5f, 1f);
        when(rock.isActive()).thenReturn(false);
        gameObjects.add(rock);

        assertThat(navigation.findPath(START, END)).containsExactly(START, END);
    }

    private static boolean everySegmentAvoids(List<Vector3> path, NavigationGrid.Obstacle obstacle) {
        NavigationGrid grid = new NavigationGrid(GameConfig.WIDTH, GameConfig.HEIGHT, List.of(obstacle),
                FlowFieldNavigation.OBSTACLE_CLEARANCE);
        for (int i = 0; i + 1 < path.size(); i++) {
            if (!grid.isSegmentClear(path.get(i), path.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static GameObject obstacleAt(float x, float z, float radius) {
        GameObject obstacle = mock(GameObject.class);
        when(obstacle.isActive()).thenReturn(true);
        when(obstacle.hasComponent(StaticObstacle.class)).thenReturn(true);
        when(obstacle.getPosition()).thenReturn(new Vector3(x, 0f, z));
        when(obstacle.getFirstCircleCollider(false))
                .thenReturn(Optional.of(new CircleCollider(obstacle, radius, false)));
        return obstacle;
    }
}
