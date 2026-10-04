package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.AttackInfo;

// Asked by Mob before any damage lands, both when a hit arrives through onDamaged and when
// something (a status effect tick, a fall) calls applyDamage directly. Unlike a DamageInterceptor
// it has no side effect on the component, so it can be asked on every path and any number of
// times for the same attempt.
public interface DamageImmunity {
    boolean isImmuneTo(AttackInfo attackInfo);
}
