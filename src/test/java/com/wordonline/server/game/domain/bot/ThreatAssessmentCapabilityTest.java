package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ThreatAssessmentCapabilityTest {

    private static final Vector3 DEFENDED = new Vector3(1, 0, 5);

    // 위아래 두 라인이 동시에 밀려오면 평균은 아무도 없는 가운데를 가리킨다.
    @Test
    void strongestLaneDoesNotAverageTwoLanesIntoTheEmptyMiddle() {
        ThreatAssessment threats = observe(List.of(
                enemy(1, new Vector3(4, 0, 1)),
                enemy(2, new Vector3(4, 0, 1.5f)),
                enemy(3, new Vector3(4, 0, 9))), false);

        assertThat(threats.contestedLaneZ()).isBetween(3f, 5f);
        assertThat(threats.strongestLaneZ()).isCloseTo(1.25f, within(0.05f));
    }

    @Test
    void strongestLaneFallsBackToTheDefendedLaneWhenNothingPresses() {
        ThreatAssessment threats = observe(List.of(enemy(1, new Vector3(16, 0, 1))), false);

        assertThat(threats.strongestLaneZ()).isEqualTo(DEFENDED.getZ());
    }

    // 강한 유닛 하나는 약한 유닛 셋보다 더 무겁게 잡혀야 한다.
    @Test
    void strengthDecidesWhichLaneIsStrongest() {
        BotVisibleObject golem = enemy(1, new Vector3(4, 0, 9));
        List<BotVisibleObject> objects = List.of(
                golem,
                enemy(2, new Vector3(4, 0, 1)),
                enemy(3, new Vector3(4, 0, 1.5f)));

        ThreatAssessment threats = ThreatAssessment.observe(
                objects, Master.RightPlayer, DEFENDED, 100,
                object -> object.id() == golem.id() ? 3.0 : 0.5, false);

        assertThat(threats.strongestLaneZ()).isEqualTo(9f);
    }

    @Test
    void strengthRaisesPressure() {
        List<BotVisibleObject> objects = List.of(enemy(1, new Vector3(4, 0, 5)));

        double counted = ThreatAssessment.observe(objects, Master.RightPlayer, DEFENDED, 100, o -> 1.0, false).pressure();
        double weighed = ThreatAssessment.observe(objects, Master.RightPlayer, DEFENDED, 100, o -> 2.0, false).pressure();

        assertThat(weighed).isGreaterThan(counted);
    }

    @Test
    void alliesInTheSameLaneTakePressureOff() {
        List<BotVisibleObject> objects = List.of(
                enemy(1, new Vector3(4, 0, 5)),
                ally(2, new Vector3(3, 0, 5)));

        double ignored = observe(objects, false).pressure();
        double covered = observe(objects, true).pressure();

        assertThat(covered).isLessThan(ignored);
    }

    @Test
    void anAllyInTheOtherLaneIsNoCover() {
        List<BotVisibleObject> objects = List.of(
                enemy(1, new Vector3(4, 0, 1)),
                ally(2, new Vector3(3, 0, 9)));

        assertThat(observe(objects, true).pressure()).isEqualTo(observe(objects, false).pressure());
    }

    @Test
    void ledByMovesThreatsAlongTheirVelocity() {
        ThreatAssessment threats = observe(List.of(enemy(1, new Vector3(8, 0, 5))), false);

        ThreatAssessment led = threats.ledBy(Map.of(1, new Vector3(-2, 0, 0)), 0.5);

        EnemyThreat moved = led.threats().getFirst();
        assertThat(moved.position()).isEqualTo(new Vector3(7, 0, 5));
        assertThat(moved.distanceToDefendedPosition()).isCloseTo(6.0, within(1e-6));
        assertThat(threats.threats().getFirst().position()).isEqualTo(new Vector3(8, 0, 5));
    }

    private static ThreatAssessment observe(List<BotVisibleObject> objects, boolean allyCover) {
        return ThreatAssessment.observe(objects, Master.RightPlayer, DEFENDED, 100, object -> 1.0, allyCover);
    }

    private static BotVisibleObject enemy(int id, Vector3 position) {
        return new BotVisibleObject(id, Master.RightPlayer, PrefabType.FireSpirit, position, 10, true, true);
    }

    private static BotVisibleObject ally(int id, Vector3 position) {
        return new BotVisibleObject(id, Master.LeftPlayer, PrefabType.FireSpirit, position, 10, true, true);
    }
}
