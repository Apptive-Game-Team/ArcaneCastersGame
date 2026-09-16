package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.mob.Mob;

import lombok.Getter;
import lombok.Setter;

public abstract class TimedBehaviorMob extends Mob implements IntervalAttacker {

    @Getter
    private final Stat attackInterval;

    @Setter
    private Behavior behavior;

    private float timer = 0;

    public TimedBehaviorMob(GameObject gameObject, int maxHp,
            float speed, float interval, Behavior behavior) {
        super(gameObject, maxHp, speed);
        this.attackInterval = new Stat(interval);
        this.behavior = behavior;
    }

    @Override
    public void update() {
        super.update();
        timer += getGameContext().getDeltaTime();
        if (timer >= attackInterval.total()) {
            if (behavior.behave()) {
                timer = 0;
            }
        }
    }
}
