package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * Summons a {@link PrefabType#Vine} under every allied {@link PrefabType#SeedSpirit} that comes
 * within {@code radius} of the building it rides on. The vine itself does the transformation: its
 * {@link OnStartSeedSpiritEvolver} replaces allied seed spirits inside the vine's radius with
 * {@link PrefabType#VineSpirit} as soon as the vine is created, and the seed spirit sits at the
 * vine's center, so it is always among them.
 *
 * <p>The check runs every tick rather than on an interval, so a seed spirit walking straight
 * through the edge of the radius is not missed between two checks. A seed spirit that an earlier
 * vine in the same tick already transformed is destroyed by then and is skipped, so a clustered
 * group gets one vine, not one per spirit.
 */
public class SeedSpiritVineSummoner extends Component {

    private final float radius;

    public SeedSpiritVineSummoner(GameObject gameObject, float radius) {
        super(gameObject);
        this.radius = Math.max(0f, radius);
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (gameObject.isDestroyed()) {
            return;
        }

        for (GameObject target : getGameContext().overlapSphereAll(gameObject, radius)) {
            if (!isAlliedSeedSpirit(target)) {
                continue;
            }
            new GameObject(gameObject.getMaster(), PrefabType.Vine, new Vector3(target.getPosition()), getGameContext());
        }
    }

    @Override
    public void onDestroy() {
    }

    private boolean isAlliedSeedSpirit(GameObject target) {
        return target != gameObject
                && !target.isDestroyed()
                && target.getType() == PrefabType.SeedSpirit
                && target.getMaster() == gameObject.getMaster();
    }
}
