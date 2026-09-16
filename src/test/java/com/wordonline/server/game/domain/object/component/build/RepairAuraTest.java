package com.wordonline.server.game.domain.object.component.build;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepairAuraTest {

    private final GameObject totem = mock(GameObject.class);
    private final GameContext gameContext = mock(GameContext.class);

    private RepairAura aura(float radius) {
        when(totem.getGameContext()).thenReturn(gameContext);
        when(totem.getMaster()).thenReturn(Master.LeftPlayer);
        return new RepairAura(totem, radius);
    }

    private GameObject allyWithSelfDestroyer(TimedSelfDestroyer selfDestroyer) {
        // TargetCategory.of() classifies a target with a Mob whose speed is 0 as BUILDING,
        // which is what repair_totem itself looks like (DummyMob(hp) with no movement).
        GameObject ally = mock(GameObject.class);
        when(ally.getMaster()).thenReturn(Master.LeftPlayer);
        when(ally.getComponent(TimedSelfDestroyer.class)).thenReturn(selfDestroyer);
        Mob mob = mock(Mob.class);
        when(mob.getSpeed()).thenReturn(new Stat(0f));
        when(ally.getComponent(Mob.class)).thenReturn(mob);
        return ally;
    }

    @Test
    void freezesTimedSelfDestroyerOfAlliedTargetsInRange() {
        TimedSelfDestroyer allySelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject ally = allyWithSelfDestroyer(allySelfDestroyer);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(ally));

        aura.update();

        verify(allySelfDestroyer).freeze();
    }

    @Test
    void freezesEveryTickSoTheHoldLastsWhileTheTargetStaysInRange() {
        TimedSelfDestroyer allySelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject ally = allyWithSelfDestroyer(allySelfDestroyer);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(ally));

        aura.update();
        aura.update();
        aura.update();

        verify(allySelfDestroyer, org.mockito.Mockito.times(3)).freeze();
    }

    @Test
    void neverRewindsElapsedTime() {
        TimedSelfDestroyer allySelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject ally = allyWithSelfDestroyer(allySelfDestroyer);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(ally));

        aura.update();

        verify(allySelfDestroyer, never()).recover();
        verify(allySelfDestroyer, never()).recover(org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void ignoresEnemyTargetsEvenWithATimedSelfDestroyer() {
        TimedSelfDestroyer enemySelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject enemy = mock(GameObject.class);
        when(enemy.getMaster()).thenReturn(Master.RightPlayer);
        when(enemy.getComponent(TimedSelfDestroyer.class)).thenReturn(enemySelfDestroyer);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(enemy));

        aura.update();

        verify(enemySelfDestroyer, never()).freeze();
    }

    @Test
    void neverFreezesItsOwnTimedSelfDestroyerEvenIfItOverlapsItself() {
        TimedSelfDestroyer ownSelfDestroyer = mock(TimedSelfDestroyer.class);
        when(totem.getComponent(TimedSelfDestroyer.class)).thenReturn(ownSelfDestroyer);

        RepairAura aura = aura(5f);
        // overlapSphereAll can include the totem itself among the results
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(totem));

        aura.update();

        verify(ownSelfDestroyer, never()).freeze();
    }

    @Test
    void doesNothingForATargetWithNoTimedSelfDestroyer() {
        GameObject ally = mock(GameObject.class);
        when(ally.getMaster()).thenReturn(Master.LeftPlayer);
        when(ally.getComponent(TimedSelfDestroyer.class)).thenReturn(null);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(ally));

        aura.update();
        // no exception thrown reaching this line is the assertion
    }

    @Test
    void doesNothingWhileItHasNoMaster() {
        GameObject ally = mock(GameObject.class);
        TimedSelfDestroyer allySelfDestroyer = mock(TimedSelfDestroyer.class);
        when(ally.getMaster()).thenReturn(Master.None);
        when(ally.getComponent(TimedSelfDestroyer.class)).thenReturn(allySelfDestroyer);

        when(totem.getGameContext()).thenReturn(gameContext);
        when(totem.getMaster()).thenReturn(Master.None);
        RepairAura aura = new RepairAura(totem, 5f);

        aura.update();

        verify(gameContext, never()).overlapSphereAll(totem, 5f);
        verify(allySelfDestroyer, never()).freeze();
    }

    @Test
    void ignoresAlliedNonBuildingTargetsWithNoMobOrDamageable() {
        // TargetCategory.of() falls back to UNKNOWN when the target has neither a Mob nor a
        // Damageable component, which is what an elemental field or a spell effect like
        // sand_storm looks like even though it carries a TimedSelfDestroyer.
        TimedSelfDestroyer fieldSelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject field = mock(GameObject.class);
        when(field.getMaster()).thenReturn(Master.LeftPlayer);
        when(field.getComponent(TimedSelfDestroyer.class)).thenReturn(fieldSelfDestroyer);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(field));

        aura.update();

        verify(fieldSelfDestroyer, never()).freeze();
    }

    @Test
    void ignoresAlliedUnitsWithPositiveSpeed() {
        // TargetCategory.of() classifies a target with a Mob whose speed is above 0 as UNIT
        // (e.g. a tadpole), which repair_totem must not freeze.
        TimedSelfDestroyer unitSelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject unit = mock(GameObject.class);
        when(unit.getMaster()).thenReturn(Master.LeftPlayer);
        when(unit.getComponent(TimedSelfDestroyer.class)).thenReturn(unitSelfDestroyer);
        Mob mob = mock(Mob.class);
        when(mob.getSpeed()).thenReturn(new Stat(1f));
        when(unit.getComponent(Mob.class)).thenReturn(mob);

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(unit));

        aura.update();

        verify(unitSelfDestroyer, never()).freeze();
    }

    @Test
    void ignoresAnotherRepairTotemEvenThoughItIsAnAlliedBuilding() {
        TimedSelfDestroyer otherTotemSelfDestroyer = mock(TimedSelfDestroyer.class);
        GameObject otherTotem = allyWithSelfDestroyer(otherTotemSelfDestroyer);
        when(otherTotem.getComponent(RepairAura.class)).thenReturn(mock(RepairAura.class));

        RepairAura aura = aura(5f);
        when(gameContext.overlapSphereAll(totem, 5f)).thenReturn(List.of(otherTotem));

        aura.update();

        verify(otherTotemSelfDestroyer, never()).freeze();
    }
}
