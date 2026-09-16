package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("vine_fan")
public class VineFanMagic extends Magic {

    private static final double FAN_ANGLE_DEGREES = 60.0;
    private static final int SHOT_COUNT = 3;

    private final VineTossMagic vineTossMagic;

    public VineFanMagic(VineTossMagic vineTossMagic) {
        this.vineTossMagic = vineTossMagic;
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        run(gameContext, master, gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null), position);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 targetPosition) {
        if (castOrigin == null || targetPosition == null) {
            return;
        }

        Vector3 centerDirection = targetPosition.subtract(castOrigin).normalize();
        if (centerDirection.equals(Vector3.ZERO)) {
            return;
        }

        for (int i = 0; i < SHOT_COUNT; i++) {
            double angleDegrees = resolveFanAngleDegrees(i);
            Vector3 direction = rotateOnGroundPlane(centerDirection, angleDegrees);
            vineTossMagic.run(gameContext, master, castOrigin, castOrigin.plus(direction));
        }
    }

    private static double resolveFanAngleDegrees(int index) {
        if (SHOT_COUNT <= 1) {
            return 0.0;
        }

        double angleStep = FAN_ANGLE_DEGREES / (SHOT_COUNT - 1);
        return -FAN_ANGLE_DEGREES / 2.0 + angleStep * index;
    }

    private static Vector3 rotateOnGroundPlane(Vector3 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector3(
                (float) (direction.getX() * cos - direction.getZ() * sin),
                direction.getY(),
                (float) (direction.getX() * sin + direction.getZ() * cos)
        ).normalize();
    }
}
