package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.dto.frame.FrameInfoDto;

class ManaChargerTest {

    private ManaCharger manaCharger;
    private PlayerData player;
    private FrameInfoDto frameInfo;

    @BeforeEach
    void setUp() {
        ManaCharger.MAX_MANA = 20;
        manaCharger = new ManaCharger(null);
        player = new PlayerData(manaCharger);
        frameInfo = mock(FrameInfoDto.class);
    }

    @Test
    void chargesTheDefaultOneManaEachInterval() {
        chargeIntervals(5);

        assertThat(player.mana).isEqualTo(5);
    }

    @Test
    void accumulatesManaWellFractionalBonusAcrossIntervals() {
        manaCharger.updateManaCharge(0.8f);

        chargeIntervals(5);

        assertThat(player.mana).isEqualTo(9);
    }

    @Test
    void discardsFractionalManaWhileAtTheMaximum() {
        manaCharger.updateManaCharge(0.8f);
        chargeIntervals(1);
        player.mana = ManaCharger.MAX_MANA;

        manaCharger.chargeMana(player, frameInfo, ManaCharger.MANA_CHARGE_INTERVAL);
        player.mana = 0;
        manaCharger.chargeMana(player, frameInfo, ManaCharger.MANA_CHARGE_INTERVAL);

        assertThat(player.mana).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void chargesTheSameManaInAMinuteAtAnyTickRate(int tickRate) {
        ManaCharger.MAX_MANA = 10_000;
        float deltaTime = 1f / tickRate;

        for (int frame = 0; frame < 60 * tickRate; frame++) {
            manaCharger.chargeMana(player, frameInfo, deltaTime);
        }

        // One mana every 0.25 s.
        assertThat(player.mana).isEqualTo(240);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void chargesTheFirstManaAfterAQuarterSecond(int tickRate) {
        float deltaTime = 1f / tickRate;

        for (int frame = 1; frame < tickRate / 4; frame++) {
            manaCharger.chargeMana(player, frameInfo, deltaTime);
        }
        assertThat(player.mana).isZero();

        manaCharger.chargeMana(player, frameInfo, deltaTime);
        assertThat(player.mana).isEqualTo(1);
    }

    private void chargeIntervals(int count) {
        for (int interval = 0; interval < count; interval++) {
            manaCharger.chargeMana(player, frameInfo, ManaCharger.MANA_CHARGE_INTERVAL);
        }
    }
}
