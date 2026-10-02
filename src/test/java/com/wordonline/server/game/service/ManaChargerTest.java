package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        manaCharger.chargeMana(player, frameInfo, 5);
        player.mana = 0;
        manaCharger.chargeMana(player, frameInfo, 10);

        assertThat(player.mana).isEqualTo(1);
    }

    private void chargeIntervals(int count) {
        int framesPerInterval = (int) (GameLoop.FPS * ManaCharger.MANA_CHARGE_INTERVAL);
        for (int interval = 0; interval < count; interval++) {
            manaCharger.chargeMana(player, frameInfo, interval * framesPerInterval);
        }
    }
}
