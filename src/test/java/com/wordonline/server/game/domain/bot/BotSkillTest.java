package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BotSkillTest {

    private static final List<BotTier> ORDINARY_TIERS = List.of(
            BotTier.INTRO, BotTier.BEGINNER, BotTier.INTERMEDIATE, BotTier.ADVANCED, BotTier.ELITE);

    // 접대 봇은 지기 위해 두므로, 이기기 위한 능력은 하나도 갖지 않는다.
    @Test
    void hospitalityKnowsNothingBeyondTheBaseScorer() {
        BotSkill skill = BotSkill.forTier(BotTier.HOSPITALITY);

        assertThat(skill.capabilities()).isEmpty();
        assertThat(skill.noiseAmplitude()).isZero();
    }

    // INTRO는 능력이 생기기 전 일반 티어가 하던 플레이를 그대로 한다.
    @Test
    void introPlaysExactlyAsEveryOrdinaryTierDidBefore() {
        assertThat(BotSkill.forTier(BotTier.INTRO).capabilities())
                .containsExactlyInAnyOrder(BotCapability.SEED_SPIRIT_COMBO, BotCapability.MOB_CLUSTER_COMBO);
    }

    @Test
    void eachTierKnowsEverythingTheTierBelowKnowsAndIsNoNoisier() {
        for (int i = 1; i < ORDINARY_TIERS.size(); i++) {
            BotSkill lower = BotSkill.forTier(ORDINARY_TIERS.get(i - 1));
            BotSkill higher = BotSkill.forTier(ORDINARY_TIERS.get(i));

            assertThat(higher.capabilities()).containsAll(lower.capabilities());
            assertThat(higher.noiseAmplitude()).isLessThanOrEqualTo(lower.noiseAmplitude());
        }
    }

    @Test
    void eliteKnowsEveryCapability() {
        assertThat(BotSkill.forTier(BotTier.ELITE).capabilities()).containsExactlyInAnyOrder(BotCapability.values());
    }

    @Test
    void withAndWithoutReturnAdjustedCopies() {
        BotSkill base = new BotSkill(0.1, Set.of(BotCapability.LETHAL));

        BotSkill added = base.with(BotCapability.ALLY_COVER);
        BotSkill removed = added.without(BotCapability.LETHAL);

        assertThat(base.capabilities()).containsExactly(BotCapability.LETHAL);
        assertThat(added.capabilities()).containsExactlyInAnyOrder(BotCapability.LETHAL, BotCapability.ALLY_COVER);
        assertThat(removed.capabilities()).containsExactly(BotCapability.ALLY_COVER);
        assertThat(removed.noiseAmplitude()).isEqualTo(0.1);
    }
}
