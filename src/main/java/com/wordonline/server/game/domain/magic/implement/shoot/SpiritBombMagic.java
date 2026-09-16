package com.wordonline.server.game.domain.magic.implement.shoot;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.SpiritBombChannel;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

@Component("spirit_bomb")
public class SpiritBombMagic extends Magic {

    public static final float ALLY_HP_FLOOR_FRACTION = 0.5f;
    public static final float DAMAGE_FRACTION = 0.7f;
    public static final float ABSORB_PROJECTILE_DURATION = 0.35f;
    public static final String ABSORB_PROJECTILE = "ElectricAbsorb";

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        run(gameContext, master, position, position);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 position) {
        GameObject player = gameContext.findPlayerGameObject(master).orElse(null);
        if (player == null) {
            return;
        }

        Vector3 direction = position.grounded().subtract(player.getPosition().grounded());
        if (direction.equals(Vector3.ZERO)) {
            return;
        }

        int absorbedHp = 0;
        for (GameObject ally : gameContext.getActiveGameObjects()) {
            if (ally == player
                    || ally.getMaster() != master
                    || ally.getType() == PrefabType.Player) {
                continue;
            }

            Mob mob = ally.getComponent(Mob.class);
            if (mob == null) {
                continue;
            }

            int drainedHp = mob.drainHpAboveFraction(ALLY_HP_FLOOR_FRACTION);
            if (drainedHp == 0) {
                continue;
            }

            absorbedHp += drainedHp;
            gameContext.getObjectsInfoDtoBuilder().createProjection(
                    ally,
                    player,
                    ABSORB_PROJECTILE,
                    ABSORB_PROJECTILE_DURATION
            );
        }

        int totalDamage = Math.round(absorbedHp * DAMAGE_FRACTION);
        player.addComponent(new SpiritBombChannel(player, position, totalDamage));
    }
}
