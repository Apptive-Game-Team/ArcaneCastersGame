package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnitCatalogueTest {

    // fire_spirit: power sqrt(10 * 10) = 10, one body for 5 mana -> 2 per mana.
    // water_slime: power sqrt(40 * 10) = 20, one body for 5 mana -> 4 per mana.
    private static final Magic WEAK = BotBrainTestFixtures.summon(1, "weak", PrefabType.FireSpirit, 1);
    private static final Magic STRONG = BotBrainTestFixtures.summon(2, "strong", PrefabType.WaterSlime, 1);

    private final Map<String, Double> values = new HashMap<>();

    @Test
    void ranksSummonsByCombatPowerPerMana() {
        UnitCatalogue units = catalogue();

        assertThat(units.relativeEfficiency(STRONG, 5)).isGreaterThan(1.0);
        assertThat(units.relativeEfficiency(WEAK, 5)).isLessThan(1.0);
        assertThat(units.averageSummonCost()).isEqualTo(5.0);
    }

    // 파라미터가 아무것도 말해 주지 않는 유닛은 평균으로 본다. 약하다고 추측하면 그 카드군 전체를
    // 피하게 되고, 강하다고 추측하면 반대가 된다.
    @Test
    void aUnitTheParametersCannotJudgeIsAverage() {
        UnitCatalogue units = catalogue();
        Magic unknown = BotBrainTestFixtures.summon(3, "unknown", PrefabType.SeedSpirit, 1);

        assertThat(units.relativeEfficiency(unknown, 5)).isEqualTo(1.0);
        assertThat(units.relativeThreat(visible(PrefabType.SeedSpirit, 10))).isEqualTo(1.0);
    }

    @Test
    void aWoundedEnemyThreatensLessThanAFreshOne() {
        UnitCatalogue units = catalogue();

        assertThat(units.relativeThreat(visible(PrefabType.WaterSlime, 10)))
                .isLessThan(units.relativeThreat(visible(PrefabType.WaterSlime, 40)));
    }

    @Test
    void theEnemyPlayerIsNotAFighter() {
        assertThat(catalogue().relativeThreat(visible(PrefabType.Player, 100))).isZero();
    }

    @Test
    void readsRangeFromTheSummonedPrefab() {
        values.put("water_slime.attack_range", UnitCatalogue.RANGED_ATTACK_RANGE + 1);
        UnitCatalogue units = catalogue();

        assertThat(units.summonsRanged(STRONG)).isTrue();
        assertThat(units.summonsRanged(WEAK)).isFalse();
    }

    private UnitCatalogue catalogue() {
        values.putIfAbsent("weak.mana_cost", 5.0);
        values.putIfAbsent("strong.mana_cost", 5.0);
        values.putIfAbsent("fire_spirit.hp", 10.0);
        values.putIfAbsent("fire_spirit.damage", 10.0);
        values.putIfAbsent("water_slime.hp", 40.0);
        values.putIfAbsent("water_slime.damage", 10.0);

        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(invocation ->
                values.getOrDefault(invocation.getArgument(0) + "." + invocation.getArgument(1),
                        invocation.<Double>getArgument(2)));
        DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
        when(parser.getAllMagics()).thenReturn(List.of(WEAK, STRONG));
        return new UnitCatalogue(parser, new BotSpellStats(parameters), parameters);
    }

    private static BotVisibleObject visible(PrefabType type, int hp) {
        return new BotVisibleObject(9, Master.RightPlayer, type, new Vector3(5, 0, 5), hp, true, true);
    }
}
