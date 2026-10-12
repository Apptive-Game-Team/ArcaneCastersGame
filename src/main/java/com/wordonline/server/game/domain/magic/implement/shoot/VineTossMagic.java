package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.VineLine;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("vine_toss")
public class VineTossMagic extends Magic {

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        run(gameContext, master, gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null), position);
    }

    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 targetPosition) {
        if (castOrigin == null || targetPosition == null) {
            return;
        }

        Vector3 direction = targetPosition.subtract(castOrigin).normalize();
        if (direction.equals(Vector3.ZERO)) {
            return;
        }

        VineLine.spawn(gameContext, master, castOrigin, direction);
    }
}
