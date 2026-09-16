package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Every value is read under the magic's own name, which is the key MagicInputHandler charges the
// cast with, so the bot never prices a cast differently from the handler.
class BotSpellStatsTest {

    private final Parameters parameters = mock(Parameters.class);
    private final BotSpellStats spellStats = new BotSpellStats(parameters);

    @Test
    void chargesOneCastReadUnderTheMagicName() {
        when(parameters.getValueOrDefault(eq("leafair"), eq("mana_cost"), anyDouble())).thenReturn(20.0);

        assertThat(spellStats.manaCost(magic("leafair"))).isEqualTo(20);
    }

    @Test
    void treatsAnUnpricedMagicAsUnaffordable() {
        when(parameters.getValueOrDefault(eq("leafair"), eq("mana_cost"), anyDouble()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        assertThat(spellStats.manaCost(magic("leafair")))
                .isEqualTo(BotSpellStats.UNKNOWN_MANA_COST);
    }

    @Test
    void readsCastRangeFromTheMagic() {
        when(parameters.getValueOrDefault(eq("razor_gale"), eq("range"), anyDouble())).thenReturn(9.0);

        assertThat(spellStats.castRange(magic("razor_gale"))).isEqualTo(9.0);
    }

    @Test
    void fallsBackToNeutralDamageWhenTheMagicHasNoDamageRow() {
        when(parameters.getValueOrDefault(eq("zap_mouse"), eq("damage"), anyDouble()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        assertThat(spellStats.damagePerTarget(magic("zap_mouse"))).isEqualTo(BotSpellStats.UNKNOWN_DAMAGE);
    }

    private static Magic magic(String name) {
        Magic magic = new Magic() {
            @Override
            public void run(GameContext gameContext, Master master, Vector3 position) {
            }
        };
        magic.name = name;
        return magic;
    }
}
