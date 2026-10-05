package com.wordonline.server.game.domain.object.component.mob;

import java.util.function.BooleanSupplier;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.DamageImmunity;
import com.wordonline.server.game.dto.Effect;

// Makes a PVE scenario installer immune to damage while shieldUp says so. shieldUp is asked on
// every damage attempt, so a source installed later raises the shield and a destroyed source
// drops it with no event in between. While the shield is up the object shows the Bubble effect,
// which the client already loads from Prefabs/Effects/Bubble.
public class PveShieldComponent extends Component implements DamageImmunity {

    private static final Effect SHIELD_EFFECT = Effect.Bubble;

    private final BooleanSupplier shieldUp;
    private boolean showing;

    public PveShieldComponent(GameObject gameObject, BooleanSupplier shieldUp) {
        super(gameObject);
        this.shieldUp = shieldUp;
    }

    public boolean isShieldUp() {
        return shieldUp.getAsBoolean();
    }

    @Override
    public boolean isImmuneTo(AttackInfo attackInfo) {
        return attackInfo.getDamage() > 0 && isShieldUp();
    }

    @Override
    public void start() {
        syncEffect();
    }

    @Override
    public void update() {
        syncEffect();
    }

    @Override
    public void onDestroy() {
        if (showing) {
            gameObject.removeEffect(SHIELD_EFFECT);
            showing = false;
        }
    }

    private void syncEffect() {
        boolean up = isShieldUp();
        if (up == showing) {
            return;
        }
        showing = up;
        if (up) {
            gameObject.addEffect(SHIELD_EFFECT);
        } else {
            gameObject.removeEffect(SHIELD_EFFECT);
        }
    }
}
