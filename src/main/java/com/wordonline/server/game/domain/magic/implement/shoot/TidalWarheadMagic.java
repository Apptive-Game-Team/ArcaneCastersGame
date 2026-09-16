package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.magic.TidalWarheadProjectile;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component("tidal_warhead")
public class TidalWarheadMagic extends Magic {

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        GameObject source = gameContext.findPlayerGameObject(master).orElse(null);
        if (source == null) {
            return;
        }

        Vector3 aimPosition = position == null ? source.getPosition() : position;
        GameObject target = selectTarget(gameContext, source, aimPosition);
        if (target == null) {
            return;
        }

        boolean targetsAir = (TargetMask.of(target) & TargetMask.AIR.bit) != 0;
        PrefabType prefabType = targetsAir
                ? PrefabType.TidalWarhead
                : PrefabType.GroundTidalWarhead;
        GameObject warhead = new GameObject(
                master,
                prefabType,
                new Vector3(source.getPosition()),
                gameContext);
        warhead.getComponent(TidalWarheadProjectile.class).setTarget(target, targetsAir);
    }

    GameObject selectTarget(GameContext gameContext, GameObject source, Vector3 aimPosition) {
        return findClosestTarget(gameContext, source, aimPosition, TargetMask.AIR.bit)
                .orElseGet(() -> findClosestTarget(
                        gameContext,
                        source,
                        aimPosition,
                        TargetMask.GROUND.bit).orElse(null));
    }

    Optional<GameObject> findClosestTarget(
            GameContext gameContext,
            GameObject source,
            Vector3 aimPosition,
            int targetMask) {
        return gameContext.getActiveGameObjects().stream()
                .filter(candidate -> TargetRelation.canAttack(source, candidate))
                .filter(candidate -> !candidate.getComponents(Damageable.class).isEmpty())
                .filter(candidate -> (TargetMask.of(candidate) & targetMask) != 0)
                .min(Comparator.comparingDouble(
                        candidate -> candidate.getPosition().distance(aimPosition)));
    }
}
