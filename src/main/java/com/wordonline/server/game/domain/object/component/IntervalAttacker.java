package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.Stat;

/**
 * A component that attacks on a repeating interval and lets an effect change how fast that
 * interval runs.
 *
 * <p>Effects that hasten or slow an attack reach every such component through this interface, so a
 * component starts taking them the moment it implements it. They used to fetch each concrete class
 * by name instead, which meant every new paced component had to be added to each effect by hand,
 * and one that was missed simply never sped up.
 */
public interface IntervalAttacker {

    Stat getAttackInterval();
}
