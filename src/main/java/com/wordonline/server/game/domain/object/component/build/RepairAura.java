package com.wordonline.server.game.domain.object.component.build;

import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.dto.Master;

/**
 * Stops the lifetime of nearby allied buildings: every tick it calls
 * {@link TimedSelfDestroyer#freeze()} on every allied target in range whose
 * {@link TargetCategory} is {@link TargetCategory#BUILDING}, so that building's next tick does
 * not age it. Units and players are left alone, and so is everything that falls through to
 * {@link TargetCategory#UNKNOWN}: a spell effect such as an elemental field carries a
 * {@link TimedSelfDestroyer} but neither a Mob nor a Damageable, and freezing one would leave it
 * burning forever. It also skips any target carrying a {@link RepairAura} of its own, so two
 * repair totems in range of each other do not hold one another alive. It does not rewind elapsed
 * time, it does not heal, and it never touches its own {@link TimedSelfDestroyer}.
 */
public class RepairAura extends Component {

    private final float radius;

    public RepairAura(GameObject gameObject, float radius) {
        super(gameObject);
        this.radius = radius;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, radius, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        if (gameObject.getMaster() == Master.None) {
            return;
        }

        getGameContext().overlapSphereAll(gameObject, radius).stream()
                .filter(this::canRepair)
                .forEach(target -> target.getComponent(TimedSelfDestroyer.class).freeze());
    }

    @Override
    public void onDestroy() {
    }

    private boolean canRepair(GameObject target) {
        if (target == gameObject) return false;
        if (target.getMaster() != gameObject.getMaster()) return false;
        if (target.getComponent(TimedSelfDestroyer.class) == null) return false;
        if (TargetCategory.of(target) != TargetCategory.BUILDING) return false;
        return target.getComponent(RepairAura.class) == null;
    }
}
