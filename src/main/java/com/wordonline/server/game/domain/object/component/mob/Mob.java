package com.wordonline.server.game.domain.object.component.mob;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.magic.ElementalChart;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.DamageInterceptor;
import com.wordonline.server.game.domain.object.component.CombatDeathListener;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.GaugeComponent;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.BaseStatusEffect;
import com.wordonline.server.game.dto.frame.GameEventDto;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.dto.frame.GaugeDto;
import com.wordonline.server.game.util.MutablePair;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Mob extends Component implements Damageable, GaugeComponent {
    @Getter
    protected int hp;
    @Getter
    protected int maxHp;
    @Getter
    protected Stat speed;

    private List<MutablePair<AttackInfo, Float>> delayedAttackInfoList = new ArrayList<>();


    @Override
    public GaugeDto getGauge() {
        return new GaugeDto(hp, maxHp, GaugeCategory.HP);
    }

    @Override
    public void onDamaged(AttackInfo attackInfo) {
        if (gameObject.isDying()) {
            return;
        }

        for (DamageInterceptor interceptor : gameObject.getComponents(DamageInterceptor.class)) {
            if (interceptor.beforeDamage(attackInfo)) {
                return;
            }
        }

        gameObject.getComponents(BaseStatusEffect.class)
                .forEach(effect ->
                        attackInfo.getElement().forEach(effect::onAttacked)
                );
        applyDamage(attackInfo);
    }

    @Override
    public void onDamaged(AttackInfo attackInfo, float delay) {
        delayedAttackInfoList.add(MutablePair.of(attackInfo, delay));
    }

    private void handleDelayedAttackInfo() {
        Iterator<MutablePair<AttackInfo, Float>> iter = delayedAttackInfoList.iterator();
        while(iter.hasNext()) {
            MutablePair<AttackInfo, Float> pair = iter.next();
            pair.setSecond(pair.getSecond() - getGameContext().getDeltaTime());
            if (pair.getSecond() <= 0) {
                onDamaged(pair.getFirst());
                iter.remove();
            }
        }
    }

    public int drainHpAboveFraction(float floorFraction) {
        int floorHp = (int) Math.ceil(maxHp * Math.clamp(floorFraction, 0f, 1f));
        int drainedHp = Math.max(0, hp - floorHp);
        if (drainedHp == 0) {
            return 0;
        }

        hp -= drainedHp;
        gameObject.applyUpdate();
        return drainedHp;
    }

    public void applyDamage(AttackInfo attackInfo) {
        if (gameObject.isDying()) {
            return;
        }

        log.trace("Mob : onDamaged hp: {} damage: {} element: {} ", hp, attackInfo.getDamage(), attackInfo.getElement());
        if (attackInfo.getAttackerId() != 0 && attackInfo.getDamage() > 0) {
            getGameContext().addEvent(GameEventDto.hit(attackInfo.getAttackerId(), gameObject.getId()));
        }
        this.hp -= attackInfo.getDamage() * ElementalChart.computePairwiseProductMultiplier(attackInfo.getElement(),gameObject.getElement().total());
        gameObject.applyUpdate();
        if (this.hp <= 0 && !gameObject.isDestroyed()) {
            if (AerialDeathFall.tryStart(this)) {
                return;
            }
            completeDeath();
        }
        else if (this.hp > maxHp) {
            this.hp = maxHp;
        }
    }

    // Whether an aerial mob should fall before it dies. Mobs whose death happens at a point --
    // an explosion, say -- override this so the effect lands where they died instead of where
    // the corpse would have come down.
    protected boolean fallsOnDeath() {
        return true;
    }

    // notifies the combat death listeners and runs the concrete death behavior
    void completeDeath() {
        gameObject.getComponents(CombatDeathListener.class)
                .forEach(CombatDeathListener::onCombatDeath);
        onDeath();
    }

    @Override
    public void update() {
        handleDelayedAttackInfo();
    }

    public abstract void onDeath();

    public Mob(GameObject gameObject, int maxHp, float speed) {
        super(gameObject);
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.speed = new Stat(speed);
    }
}
