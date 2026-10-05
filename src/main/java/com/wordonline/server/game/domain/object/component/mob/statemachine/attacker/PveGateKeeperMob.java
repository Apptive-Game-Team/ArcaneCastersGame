package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;

// A PVE gate keeper: it stands where the scenario installed it, never moves and never attacks. It
// only takes damage, and it counts as an objective once its hp is gone. Unlike a unit it leaves no
// element field where it dies.
public class PveGateKeeperMob extends Mob implements PveObjectiveTarget {

    public PveGateKeeperMob(GameObject gameObject, int maxHp) {
        super(gameObject, maxHp, 0f);
    }

    @Override
    public void start() {
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    protected boolean leavesDeathField() {
        return false;
    }

    @Override
    public boolean isTerminal() {
        return gameObject.isDestroyed() || getHp() <= 0;
    }
}
