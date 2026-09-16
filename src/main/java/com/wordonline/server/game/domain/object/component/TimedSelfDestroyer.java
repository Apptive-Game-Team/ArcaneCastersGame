package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.dto.frame.GaugeDto;

public class TimedSelfDestroyer extends Component implements GaugeComponent {

    protected final float timeToLive;
    protected float elapsedTime;

    // Set by freeze(), consumed by the next update(). While it is set, that update() skips the
    // tick entirely: elapsedTime does not advance and the object is not destroyed. It holds for
    // one update only, so a source that wants the lifetime stopped has to call freeze() every
    // tick, and several sources freezing the same frame still cost exactly one skipped tick.
    protected boolean frozen;

    public TimedSelfDestroyer(GameObject gameObject, float timeToLive) {
        super(gameObject);
        this.timeToLive = timeToLive;
        this.elapsedTime = 0f;
        this.frozen = false;
    }

    @Override
    public void start() { }

    @Override
    public void update() {
        if (frozen) {
            frozen = false;
            return;
        }

        elapsedTime += getGameContext().getDeltaTime();
        if (elapsedTime >= timeToLive) {
            gameObject.destroy();
        }
    }

    @Override
    public void onDestroy() { }

    /**
     * Stops the lifetime for the next {@link #update()}: that tick does not age the object and
     * cannot destroy it. The elapsed time is not rewound. Call this every tick to keep the
     * lifetime stopped.
     */
    public void freeze() {
        frozen = true;
    }

    public void recover() {
        elapsedTime = 0f;
        gameObject.applyUpdate();
    }

    public void recover(float amount) {
        if (amount <= 0f) {
            return;
        }

        elapsedTime = Math.max(0f, elapsedTime - amount);
        gameObject.applyUpdate();
    }

    @Override
    public GaugeDto getGauge() {
        return new GaugeDto(timeToLive - elapsedTime, timeToLive, GaugeCategory.TTL);
    }
}
