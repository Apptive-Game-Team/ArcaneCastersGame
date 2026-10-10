package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.magic.implement.spawn.AbstractSpawnMagic;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PVEBossMob extends BehaviorMob implements PveObjectiveTarget {

    // Absorbs the rounding of summing deltaTime in float, so a 2 s cooldown counted down in
    // 1/60 s steps is ready on the 120th frame rather than the 121st.
    private static final float COOLDOWN_EPSILON_SECONDS = 1e-4f;

    protected final List<Magic> magics;
    // Seconds of game time before each magic may be cast again, counted down every frame.
    private final Map<Long, Float> cooldownRemaining = new HashMap<>();

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

    @Override
    public void update() {
        float deltaTime = getGameContext().getDeltaTime();
        cooldownRemaining.replaceAll((magicId, remaining) -> Math.max(0f, remaining - deltaTime));
        super.update();
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
        if (!isCooldownReady(magic)) {
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

        setCooldown(magic);
        return true;
    }

    private boolean isCooldownReady(Magic magic) {
        return cooldownRemaining.getOrDefault(magic.id, 0f) <= COOLDOWN_EPSILON_SECONDS;
    }

    private void setCooldown(Magic magic) {
        cooldownRemaining.put(magic.id, resolveCooldownSec(magic));
    }

    private static float resolveCooldownSec(Magic magic) {
        return 2.0f;
    }

    @Override
    public boolean isTerminal() {
        return gameObject.isDestroyed() || getHp() <= 0;
    }
}
