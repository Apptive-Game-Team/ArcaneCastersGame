package com.wordonline.server.game.domain.magic.implement.explode;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.VineHitTracker;
import com.wordonline.server.game.domain.object.component.magic.VineSpawnContext;
import com.wordonline.server.game.domain.object.component.magic.VineWorldGrowth;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("vine_world")
public class VineWorldMagic extends Magic {

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        if (position == null) {
            return;
        }

        VineHitTracker giantVineHitTracker = new VineHitTracker();
        GameObject[] giantVineReference = new GameObject[1];
        VineSpawnContext.runWithTracker(
                giantVineHitTracker,
                () -> giantVineReference[0] =
                        new GameObject(master, PrefabType.GiantVine, position, gameContext)
        );
        giantVineReference[0].addComponent(new VineWorldGrowth(
                giantVineReference[0],
                giantVineHitTracker
        ));
    }
}
