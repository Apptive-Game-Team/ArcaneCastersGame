package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;

/**
 * One effect application together with the object that caused it.
 * <p>
 * Receivers that react differently depending on who sent the effect read the source from here,
 * for example a lightning summon that only overcharges from its own side. Carrying the extra
 * context in one object keeps the {@code onReceive} signatures from growing a parameter per
 * question a receiver might ask.
 */
public record EffectApplication(Effect effect, GameObject source) {

    /**
     * Neutral sources such as fields belong to no side, so they count as friendly to everyone.
     * An unknown source is treated the same way, which keeps callers that have nothing to pass
     * on the old behaviour.
     */
    public static boolean isFriendly(GameObject source, GameObject target) {
        return source == null
                || source.getMaster() == Master.None
                || source.getMaster() == target.getMaster();
    }

    public boolean isFriendlyTo(GameObject target) {
        return isFriendly(source, target);
    }
}
