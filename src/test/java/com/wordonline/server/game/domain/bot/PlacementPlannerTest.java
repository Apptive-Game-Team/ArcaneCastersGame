package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlacementPlannerTest {

    private static final Random FIXED_RANDOM = new Random(1);

    @Test
    void dropsTheBlockerInTheContestedLaneAheadOfTheDefendedPlayer() {
        ThreatAssessment threats = threatsAgainstLeft(new Vector3(7, 0, 2));

        Vector3 placement = PlacementPlanner.plan(
                GameConfig.LEFT_PLAYER_POSITION, 6, Master.LeftPlayer, threats, FIXED_RANDOM);

        assertThat(placement.getZ()).isEqualTo(2f);
        assertThat(placement.getX()).isGreaterThan(GameConfig.LEFT_PLAYER_POSITION.getX());
    }

    @Test
    void placesTowardsTheEnemyForTheRightSideBot() {
        ThreatAssessment threats = threatsAgainstRight(new Vector3(11, 0, 8));

        Vector3 placement = PlacementPlanner.plan(
                GameConfig.RIGHT_PLAYER_POSITION, 6, Master.RightPlayer, threats, FIXED_RANDOM);

        assertThat(placement.getZ()).isEqualTo(8f);
        assertThat(placement.getX()).isLessThan(GameConfig.RIGHT_PLAYER_POSITION.getX());
    }

    @Test
    void advancesTowardsTheEnemyWhenTheFieldIsClear() {
        ThreatAssessment threats = threatsAgainstLeft();

        Vector3 placement = PlacementPlanner.plan(
                GameConfig.LEFT_PLAYER_POSITION, 6, Master.LeftPlayer, threats, FIXED_RANDOM);

        assertThat(placement.getX())
                .isGreaterThan(GameConfig.LEFT_PLAYER_POSITION.getX() + 4f);
    }

    @Test
    void neverPlacesOutsideTheMapOrBeyondTheCastRange() {
        ThreatAssessment threats = threatsAgainstRight(new Vector3(17, 0, 10));
        Vector3 caster = GameConfig.RIGHT_PLAYER_POSITION;

        Vector3 placement = PlacementPlanner.plan(caster, 18, Master.RightPlayer, threats, FIXED_RANDOM);

        assertThat(placement.getX()).isBetween(0f, (float) GameConfig.WIDTH);
        assertThat(placement.getZ()).isBetween(0f, (float) GameConfig.HEIGHT);
        assertThat(caster.distance(placement)).isLessThanOrEqualTo(18);
    }

    // 원거리 유닛은 근접 유닛이 막을 자리보다 뒤에 선다.
    @Test
    void placesARangedUnitBehindWhereABlockerWouldStand() {
        ThreatAssessment threats = threatsAgainstLeft(new Vector3(9, 0, 5));
        Vector3 caster = GameConfig.LEFT_PLAYER_POSITION;

        Vector3 blocker = PlacementPlanner.plan(
                caster, 10, Master.LeftPlayer, threats, new PlacementPlanner.Options(false, false), FIXED_RANDOM);
        Vector3 ranged = PlacementPlanner.plan(
                caster, 10, Master.LeftPlayer, threats, new PlacementPlanner.Options(false, true), FIXED_RANDOM);

        assertThat(ranged.getX()).isGreaterThan(caster.getX());
        assertThat(ranged.getX()).isLessThan(blocker.getX());
    }

    @Test
    void defendsTheStrongestLaneInsteadOfTheMiddleBetweenTwo() {
        ThreatAssessment threats = threatsAgainstLeft(
                new Vector3(4, 0, 1), new Vector3(4, 0, 1.5f), new Vector3(4, 0, 9));

        Vector3 averaged = PlacementPlanner.plan(GameConfig.LEFT_PLAYER_POSITION, 10, Master.LeftPlayer, threats,
                PlacementPlanner.Options.BASIC, FIXED_RANDOM);
        Vector3 strongest = PlacementPlanner.plan(GameConfig.LEFT_PLAYER_POSITION, 10, Master.LeftPlayer, threats,
                new PlacementPlanner.Options(true, false), FIXED_RANDOM);

        assertThat(averaged.getZ()).isBetween(3f, 5f);
        assertThat(strongest.getZ()).isLessThan(2f);
    }

    private static ThreatAssessment threatsAgainstLeft(Vector3... enemyPositions) {
        return observe(Master.RightPlayer, GameConfig.LEFT_PLAYER_POSITION, enemyPositions);
    }

    private static ThreatAssessment threatsAgainstRight(Vector3... enemyPositions) {
        return observe(Master.LeftPlayer, GameConfig.RIGHT_PLAYER_POSITION, enemyPositions);
    }

    private static ThreatAssessment observe(Master enemySide, Vector3 defended, Vector3... enemyPositions) {
        List<BotVisibleObject> enemies = Stream.of(enemyPositions)
                .map(position -> BotVisibleObject.of(enemy(enemySide, position)))
                .toList();
        return ThreatAssessment.observe(enemies, enemySide, defended, 100);
    }

    private static GameObject enemy(Master master, Vector3 position) {
        GameObject gameObject = new GameObject(
                master, PrefabType.RockSlime, position, mock(GameContext.class));
        gameObject.setStatus(Status.Idle);
        return gameObject;
    }
}
