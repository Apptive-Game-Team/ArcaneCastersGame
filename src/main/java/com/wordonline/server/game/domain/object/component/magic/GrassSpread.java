package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;

/**
 * Spreads {@link PrefabType#LeafField} around the building it rides on, one field per
 * {@code attackInterval}, into a fixed table of {@code ringCount * quantityPerRing} slots. Slots
 * fill innermost ring first, then in slot order within a ring; a field destroyed at any slot frees
 * exactly that slot, so the next spread refills it instead of moving outward. Each position is
 * picked with {@code VineWorldGrowth.spawnRing()}'s {@code cos/sin(angle) * radius} polar
 * placement.
 *
 * <p>A leaf field carries its own {@code TimedSelfDestroyer(leaf_field.duration)}, 3 seconds, from
 * {@code AbstractFieldPrefabInitializer}. While this building is alive, {@link #update()} calls
 * {@link TimedSelfDestroyer#freeze()} on every planted field's timer, every tick, so none of them
 * expire on their own and the carpet only shrinks when a field is destroyed some other way (e.g.
 * burned). Once this building is destroyed, {@code ComponentUpdateSystem} stops calling this
 * class's {@code update()}: the {@code freeze()} calls stop and each surviving field's own 3-second
 * timer resumes from wherever it had reached. No cleanup happens here for that; the wilting is the
 * absence of freezing.
 *
 * <p>The timer for these two events is cached at plant time rather than looked up every tick,
 * because {@code GameObject.getComponent()} scans the component list and freezing runs on up to
 * {@code ringCount * quantityPerRing} fields every tick. {@code GameObject}'s constructor calls
 * {@code gameContext.createGameObject()}, which runs {@code gameObject.start()} synchronously, so
 * the planted field's {@link TimedSelfDestroyer} already exists by the time the constructor
 * returns; if a field somehow has none, the cached reference is null and is simply not frozen.
 *
 * <p>{@code LeafFieldEffectReceiver} destroys a leaf field on {@link Effect#Burn} and leaves a
 * {@code fire_field} (3-second duration) at the same spot. {@code attack_interval} is 1 second, so
 * refilling that slot before the fire field burns out would plant a new leaf field inside it, which
 * burns immediately and drops another fire field: a self-sustaining loop that renews a 3-second
 * fire field every second and also damages this building's own side, since the fire field belongs
 * to {@code Master.None}.
 * {@link #spreadOne()} avoids this by testing the candidate position with
 * {@code getGameContext().overlapSphereAll(position, leafFieldRadius)} before planting: a position
 * covered by anything with an {@link EffectProvider} supplying {@link Effect#Burn} is skipped for
 * this tick, and the slot is retried on the next spread once the fire field there has expired.
 */
public class GrassSpread extends MagicComponent {

    // Matches the spacing VineWorldGrowth already uses between its inner and outer vine rings
    // (4f - 2.25f = 1.75f), so a grass generator's rings sit at roughly the same density as
    // ground effects players already see from other buildings.
    private static final float RING_SPACING = 1.75f;

    private final float attackInterval;
    private final float radius;
    private final float leafFieldRadius;
    private final int quantityPerRing;
    private final int ringCount;
    private final Slot[] slots;

    private float elapsed;

    public GrassSpread(GameObject gameObject, float attackInterval, float radius, int quantityPerRing,
                        float leafFieldRadius) {
        super(gameObject);
        this.attackInterval = Math.max(0.05f, attackInterval);
        this.radius = Math.max(0f, radius);
        this.quantityPerRing = Math.max(1, quantityPerRing);
        this.ringCount = Math.max(1, (int) Math.ceil(this.radius / RING_SPACING));
        this.leafFieldRadius = Math.max(0f, leafFieldRadius);
        this.slots = new Slot[this.ringCount * this.quantityPerRing];
    }

    @Override
    public void update() {
        if (gameObject.isDestroyed()) {
            return;
        }

        for (int index = 0; index < slots.length; index++) {
            if (slots[index] != null && slots[index].field().isDestroyed()) {
                slots[index] = null;
            }
        }

        for (Slot slot : slots) {
            if (slot != null && slot.selfDestroyer() != null) {
                slot.selfDestroyer().freeze();
            }
        }

        elapsed += getGameContext().getDeltaTime();
        while (elapsed >= attackInterval) {
            elapsed -= attackInterval;
            spreadOne();
        }
    }

    private void spreadOne() {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] != null) {
                continue;
            }

            Vector3 position = positionForSlot(index);
            if (isBurning(position)) {
                continue;
            }

            GameObject field = new GameObject(gameObject.getMaster(), PrefabType.LeafField, position, getGameContext());
            slots[index] = new Slot(field, field.getComponent(TimedSelfDestroyer.class));
            return;
        }
    }

    private Vector3 positionForSlot(int index) {
        int ringIndex = index / quantityPerRing;
        int slotIndex = index % quantityPerRing;

        float ringRadius = (ringIndex == ringCount - 1) ? radius : RING_SPACING * (ringIndex + 1);
        float angleOffset = (ringIndex % 2 == 0) ? 0f : (float) Math.PI / quantityPerRing;
        double angle = angleOffset + Math.PI * 2 * slotIndex / quantityPerRing;

        Vector3 center = gameObject.getPosition();
        return center.plus(
                (float) Math.cos(angle) * ringRadius,
                0,
                (float) Math.sin(angle) * ringRadius
        );
    }

    private boolean isBurning(Vector3 position) {
        return getGameContext().overlapSphereAll(position, leafFieldRadius).stream()
                .anyMatch(GrassSpread::suppliesBurn);
    }

    private static boolean suppliesBurn(GameObject object) {
        return object.getComponents(EffectProvider.class).stream()
                .anyMatch(provider -> provider.getEffect() == Effect.Burn);
    }

    private record Slot(GameObject field, TimedSelfDestroyer selfDestroyer) {
    }
}
