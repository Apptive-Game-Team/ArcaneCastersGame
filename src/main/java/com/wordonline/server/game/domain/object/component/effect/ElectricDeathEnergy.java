package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.CombatDeathListener;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ReferenceProjectileTarget;

import java.util.Comparator;

public class ElectricDeathEnergy extends Component implements CombatDeathListener {
    private static final String ABSORB_PROJECTILE_TYPE = "ElectricAbsorb";
    private static final float ABSORB_PROJECTILE_DURATION = 0.35f;

    private boolean consumed;

    public ElectricDeathEnergy(GameObject gameObject) {
        super(gameObject);
    }

    @Override
    public void onCombatDeath() {
        if (consumed) {
            return;
        }
        consumed = true;

        Vector3 deathPosition = new Vector3(gameObject.getPosition());
        // 전기 필드 자체는 Mob 이 죽을 때 DeathField 가 원소에 따라 깐다.

        var fieldParameters = getGameContext().getParameters().object(GameObjectKey.ELECTRIC_FIELD);
        float radius = fieldParameters.floatValue(ParameterKey.RADIUS);
        float duration = fieldParameters.floatValue(ParameterKey.DURATION);
        findAbsorber(deathPosition, radius).ifPresent(absorber -> {
            OverchargeStatusEffect.apply(absorber, duration);
            getGameContext().getObjectsInfoDtoBuilder().createProjection(
                    new PositionProjectileTarget(deathPosition),
                    new ReferenceProjectileTarget(absorber.getId()),
                    ABSORB_PROJECTILE_TYPE,
                    ABSORB_PROJECTILE_DURATION
            );
        });
    }

    private java.util.Optional<GameObject> findAbsorber(Vector3 deathPosition, float radius) {
        return getGameContext().getActiveGameObjects().stream()
                .filter(candidate -> candidate != gameObject)
                .filter(GameObject::isActive)
                .filter(candidate -> candidate.getMaster() == gameObject.getMaster())
                .filter(candidate -> candidate.getElement().nativeHas(ElementType.LIGHTNING))
                .filter(candidate -> candidate.getComponent(Mob.class) != null)
                .filter(candidate -> !candidate.getComponents(Damageable.class).isEmpty())
                .filter(candidate -> !candidate.getComponents(ElectricDeathEnergy.class).isEmpty())
                .filter(candidate -> candidate.getPosition().distance(deathPosition) <= radius)
                .min(Comparator.comparingDouble(candidate -> candidate.getPosition().distance(deathPosition)));
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
    }

    @Override
    public void onDestroy() {
    }
}
