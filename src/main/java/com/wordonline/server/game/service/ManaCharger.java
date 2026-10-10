package com.wordonline.server.game.service;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.util.IntervalTimer;

import lombok.RequiredArgsConstructor;

// this class is responsible for charging mana for players
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ManaCharger {
    public final static float MANA_CHARGE_INTERVAL = 0.25f;
    public final static int DEFAULT_MANA_CHARGE_VALUE = 1;
    private static final double MANA_EPSILON = 1e-6;
    public static int MAX_MANA;

    private final Parameters parameters;

    private final Stat manaChangeValue = new Stat(DEFAULT_MANA_CHARGE_VALUE);
    private double pendingMana;
    private final IntervalTimer chargeTimer = new IntervalTimer();

    @PostConstruct
    public void initMaxMana() {
        var playerParameters = parameters.object(GameObjectKey.PLAYER);
        MAX_MANA = playerParameters.intValue(ParameterKey.MAX_MANA);
    }

    public void updateManaCharge(float deltaValue) {
        manaChangeValue.addPercent(deltaValue);
    }

    // this method is called every frame to charge mana, once per MANA_CHARGE_INTERVAL of game time
    public void chargeMana(PlayerData player, FrameInfoDto frameInfoDto, float deltaTime) {
        int intervals = chargeTimer.advance(deltaTime, MANA_CHARGE_INTERVAL);
        for (int i = 0; i < intervals; i++) {
            chargeAvailableMana(player);
        }

        frameInfoDto.setUpdatedMana(player.mana);
    }

    private void chargeAvailableMana(PlayerData player) {
        if (player.mana >= MAX_MANA) {
            pendingMana = 0;
            return;
        }

        pendingMana += manaChangeValue.total();
        int manaToAdd = (int) Math.floor(pendingMana + MANA_EPSILON);
        int availableCapacity = MAX_MANA - player.mana;

        if (manaToAdd >= availableCapacity) {
            player.addMana(availableCapacity, MAX_MANA);
            pendingMana = 0;
            return;
        }

        if (manaToAdd > 0) {
            player.addMana(manaToAdd, MAX_MANA);
            pendingMana -= manaToAdd;
            if (pendingMana < MANA_EPSILON) {
                pendingMana = 0;
            }
        }
    }
}
