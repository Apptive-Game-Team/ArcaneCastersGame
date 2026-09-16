package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.magic.implement.spawn.AbstractSpawnMagic;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.service.GameLoop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PVEBossMob extends BehaviorMob implements PveObjectiveTarget {

    protected final List<Magic> magics;
    private final Map<Long, Integer> cooldownUntilFrame = new HashMap<>();

    public PVEBossMob(GameObject gameObject,
                      int maxHp,
                      float speed,
                      int targetMask,
                      float attackInterval,
                      float attackRange,
                      List<Magic> magics) {
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, null);
        this.magics = List.copyOf(magics);
        this.setBehavior(this::castMagic);
    }

    protected boolean castMagic(GameObject target) {
        return castMagicsInOrder(target, magics);
    }

    protected boolean castMagicsInOrder(GameObject target, List<Magic> magicOrder) {
        if (magicOrder == null || magicOrder.isEmpty()) {
            return false;
        }
        for (Magic magic : magicOrder) {
            if (tryCastMagic(magic, target)) {
                return true;
            }
        }
        return false;
    }

    protected boolean tryCastMagic(Magic magic, GameObject target) {
        if (magic == null) {
            return false;
        }
        int currentFrame = getGameContext().getFrameNum();
        if (!isCooldownReady(magic, currentFrame)) {
            return false;
        }

        // A spawn puts its bodies down where the caster stands; everything else lands on the target.
        GameObject castTarget = magic instanceof AbstractSpawnMagic ? gameObject : target;
        if (castTarget == null) {
            return false;
        }

        var result = getGameContext().getMagicInputHandler().handleBotMagicInput(
                getGameContext(),
                gameObject.getMaster(),
                magic,
                castTarget.getPosition(),
                gameObject.getPosition()
        );
        if (!result.valid()) {
            return false;
        }

        setCooldown(magic, currentFrame);
        return true;
    }

    private boolean isCooldownReady(Magic magic, int currentFrame) {
        return cooldownUntilFrame.getOrDefault(magic.id, 0) <= currentFrame;
    }

    private void setCooldown(Magic magic, int currentFrame) {
        int cooldownFrames = (int) Math.ceil(resolveCooldownSec(magic) * GameLoop.FPS);
        cooldownUntilFrame.put(magic.id, currentFrame + cooldownFrames);
    }

    private static double resolveCooldownSec(Magic magic) {
        return 2.0;
    }

    @Override
    public boolean isTerminal() {
        return gameObject.isDestroyed() || getHp() <= 0;
    }
}
