package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

import java.util.List;

/**
 * A storm cloud that strikes the column of field below it a fixed number of times.
 * <p>
 * The strike is a plain overlap check, not a spawned object: nothing about it needs to live
 * between frames, and the client draws the bolt on the cloud's own sprite.
 */
public class LightningCloud extends MagicComponent {

    // how long the client takes to grow the bolt out of the cloud on one strike
    private static final float STRIKE_VISUAL_DURATION = 0.3f;

    private final float strikeInterval;
    private final int strikeCount;
    private final int damage;
    private final Vector3 boxSize;
    private float timer;
    private int strikes;
    private float lastStrikeVisualTimer;

    public LightningCloud(GameObject gameObject, float strikeInterval, int strikeCount, int damage, float radius) {
        super(gameObject);
        this.strikeInterval = strikeInterval;
        this.strikeCount = strikeCount;
        this.damage = damage;
        this.boxSize = new Vector3(radius * 2, GameConfig.DROP_MAGIC_INITIAL_HEIGHT, radius * 2);
    }

    @Override
    public void start() {
        if (strikeCount <= 0) {
            gameObject.destroy();
            return;
        }

        gameObject.drawBox(new Vector3(0, boxSize.getY() / 2, 0), boxSize, GizmoCategory.AreaOfEffect);
        strike();
    }

    @Override
    public void update() {
        if (strikes >= strikeCount) {
            // the last bolt is still growing on the client, so the cloud outlives its last strike
            lastStrikeVisualTimer += getGameContext().getDeltaTime();
            if (lastStrikeVisualTimer >= STRIKE_VISUAL_DURATION) {
                gameObject.destroy();
            }
            return;
        }

        timer += getGameContext().getDeltaTime();
        while (strikes < strikeCount && timer >= strikeInterval) {
            timer -= strikeInterval;
            strike();
        }
    }

    private void strike() {
        if (strikes >= strikeCount) {
            return;
        }

        // the client draws the bolt on the cloud itself, growing out of its underside
        gameObject.setStatus(Status.Attack);
        strikeColumnBelow();
        strikes++;
    }

    private void strikeColumnBelow() {
        Vector3 position = gameObject.getPosition();
        Vector3 boxCenter = new Vector3(position.getX(), boxSize.getY() / 2, position.getZ());
        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);

        List<GameObject> targets = getGameContext().getPhysics().overlapBoxAll(boxCenter, boxSize);
        Master owner = gameObject.getMaster();

        for (GameObject target : targets) {
            if (target == gameObject || target.isDestroyed()) continue;
            if (owner != Master.None && target.getMaster() == owner) {
                // the strike charges the caster's own lightning summons instead of damaging them
                LightningSummonEffectReceiver.overcharge(target);
                continue;
            }

            List<Damageable> damageables = target.getComponents(Damageable.class);
            if (damageables.isEmpty()) continue;

            target.setStatus(Status.Damaged);
            damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
        }

        new GameObject(
                Master.None,
                PrefabType.ElectricField,
                position.grounded(),
                getGameContext());
    }
}
