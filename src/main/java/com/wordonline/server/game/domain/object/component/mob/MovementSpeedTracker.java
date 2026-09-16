package com.wordonline.server.game.domain.object.component.mob;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.dto.Effect;

import java.util.EnumSet;
import java.util.Set;

public class MovementSpeedTracker extends Component {
    private static final int SAMPLE_FRAME_COUNT = 3;
    private static final Set<Effect> CHARGE_EFFECTS = EnumSet.of(
            Effect.StormStagCharge2,
            Effect.StormStagCharge3,
            Effect.StormStagCharge4);

    private final float maxSpeed;
    private Vector3 samplePosition;
    private int elapsedFrames;
    private float elapsedSeconds;
    private int tier = 1;
    private boolean tracking;

    public MovementSpeedTracker(GameObject gameObject, float maxSpeed) {
        super(gameObject);
        this.maxSpeed = maxSpeed;
    }

    @Override
    public void start() {
        samplePosition = new Vector3(gameObject.getPosition());
    }

    @Override
    public void update() {
        if (!tracking) {
            return;
        }

        elapsedFrames++;
        elapsedSeconds += getGameContext().getDeltaTime();
        if (elapsedFrames < SAMPLE_FRAME_COUNT || elapsedSeconds <= 0f) {
            return;
        }

        double distance = samplePosition.grounded().distance(gameObject.getPosition().grounded());
        float measuredSpeed = (float) (distance / elapsedSeconds);
        updateTier(tierFor(measuredSpeed, maxSpeed));
        samplePosition = new Vector3(gameObject.getPosition());
        elapsedFrames = 0;
        elapsedSeconds = 0f;
    }

    public void begin() {
        reset();
        tracking = true;
    }

    public void reset() {
        tracking = false;
        elapsedFrames = 0;
        elapsedSeconds = 0f;
        samplePosition = new Vector3(gameObject.getPosition());
        tier = 1;
        gameObject.removeEffects(CHARGE_EFFECTS::contains);
    }

    public int getTier() {
        return tier;
    }

    static int tierFor(float currentSpeed, float maxSpeed) {
        if (maxSpeed <= 0f) {
            return 1;
        }
        float percentage = currentSpeed / maxSpeed * 100f;
        if (percentage >= 100f) return 4;
        if (percentage >= 60f) return 3;
        if (percentage >= 30f) return 2;
        return 1;
    }

    private void updateTier(int nextTier) {
        if (tier == nextTier) {
            return;
        }
        tier = nextTier;
        gameObject.removeEffects(CHARGE_EFFECTS::contains);
        switch (tier) {
            case 2 -> gameObject.addEffect(Effect.StormStagCharge2);
            case 3 -> gameObject.addEffect(Effect.StormStagCharge3);
            case 4 -> gameObject.addEffect(Effect.StormStagCharge4);
            default -> { }
        }
    }

    @Override
    public void onDestroy() {
        gameObject.removeEffects(CHARGE_EFFECTS::contains);
    }
}
