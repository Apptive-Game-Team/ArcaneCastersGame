package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

class UnitGuideTest {

    private final Parameters parameters = windSpiritParameters();
    private final UnitGuide guide = new UnitGuide(parameters);

    @Test
    void readsTheGameObjectNamedLikeThePrefabWithoutItsSuffix() {
        assertThat(UnitGuide.objectName(PrefabType.WindSpirit)).isEqualTo("wind_spirit");
    }

    @Test
    void describesAUnitFromItsParameters() {
        assertThat(guide.describe(PrefabType.WindSpirit))
                .isEqualTo("flying, 50 hp, hits for 300 every 3s (100 dps) at range 1.5, speed 1.5");
    }

    @Test
    void theRoleSaysInWordsWhereTheUnitGoesAndWhatItHits() {
        assertThat(guide.role(PrefabType.WindSpirit)).isEqualTo(
                "flies toward the enemy base at speed 1.5, fighting what it meets; 50 hp; "
                        + "hits for 300 every 3s (100 dps) at range 1.5");
    }

    @Test
    void aTypeWithNoRowsIsUnknownRatherThanGuessed() {
        assertThat(guide.describe(PrefabType.FireSlime)).isEqualTo("no stats known");
    }

    @Test
    void secondsToReachSubtractTheAttackRange() {
        UnitGuide.Stats stats = guide.stats(PrefabType.WindSpirit);

        assertThat(stats.secondsToReach(7.5)).isEqualTo(4.0);
        assertThat(stats.secondsToReach(1.0)).isZero();
    }

    private static Parameters windSpiritParameters() {
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenReturn(0.0);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("hp"), anyDouble())).thenReturn(50.0);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("damage"), anyDouble())).thenReturn(300.0);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("attack_interval"), anyDouble())).thenReturn(3.0);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("attack_range"), anyDouble())).thenReturn(1.5);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("speed"), anyDouble())).thenReturn(1.5);
        when(parameters.getValueOrDefault(eq("wind_spirit"), eq("spawn_height"), anyDouble())).thenReturn(3.0);
        return parameters;
    }
}
