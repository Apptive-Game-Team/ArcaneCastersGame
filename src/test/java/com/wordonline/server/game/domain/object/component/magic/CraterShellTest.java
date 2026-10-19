package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CraterShellTest {
    @Test
    void waitsForLandingThenHitsOnlyGroundEnemiesOnceEvenAfterShooterDies() {
        GameContext context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(0.25f);
        GameObject shooter = object(context, Master.LeftPlayer, Vector3.ZERO);
        shooter.setElement(EnumSet.of(ElementType.FIRE, ElementType.ROCK));
        GameObject shell = object(context, Master.LeftPlayer, Vector3.ZERO);
        GameObject enemy = object(context, Master.RightPlayer, new Vector3(6, 0, 0));
        DummyMob victim = addMob(enemy);
        GameObject ally = object(context, Master.LeftPlayer, new Vector3(6, 0, 0));
        DummyMob friend = addMob(ally);
        GameObject air = object(context, Master.RightPlayer, new Vector3(6, 2, 0));
        DummyMob flier = addMob(air);
        GameObject dead = object(context, Master.RightPlayer, new Vector3(6, 0, 0));
        DummyMob corpse = addMob(dead);
        dead.destroy();
        when(context.overlapSphereAll(shell, 1.4f)).thenReturn(List.of(enemy, ally, air, dead));
        CraterShell flight = new CraterShell(shell, 6f, 1.4f);
        Vector3 point = new Vector3(6, 0, 0);
        flight.launch(point, 40, shooter);
        point.setX(12); // launch must copy, rather than retain a mutable aim vector
        shooter.destroy();
        clearInvocations(context);

        flight.update();
        flight.update();
        flight.update();
        assertThat(victim.getHp()).isEqualTo(100);
        assertThat(shell.getPosition().getY()).isGreaterThan(0);
        flight.update();
        flight.update();

        assertThat(shell.getPosition().getX()).isEqualTo(6);
        assertThat(shell.getPosition().getY()).isZero();
        assertThat(victim.getHp()).isEqualTo(60);
        assertThat(friend.getHp()).isEqualTo(100);
        assertThat(flier.getHp()).isEqualTo(100);
        assertThat(corpse.getHp()).isEqualTo(100);
        assertThat(shell.isDestroyed()).isTrue();
        verify(context, times(1)).overlapSphereAll(shell, 1.4f);
        verify(context, times(1)).createGameObject(argThat(o -> o.getType() == PrefabType.CraterExplosion));
        verify(context).addEvent(argThat(event -> event != null));
    }

    @Test
    void zeroDistanceShotStillHasFiniteFlightAndSingleImpact() {
        GameContext context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(0.05f);
        GameObject shooter = object(context, Master.LeftPlayer, Vector3.ZERO);
        GameObject shell = object(context, Master.LeftPlayer, Vector3.ZERO);
        when(context.overlapSphereAll(shell, 1f)).thenReturn(List.of());
        CraterShell flight = new CraterShell(shell, 6f, 1f);
        flight.launch(Vector3.ZERO, 40, shooter);
        clearInvocations(context);
        flight.update();
        assertThat(shell.isDestroyed()).isFalse();
        flight.update();
        flight.update();
        assertThat(shell.isDestroyed()).isTrue();
        verify(context, times(1)).overlapSphereAll(shell, 1f);
    }

    private DummyMob addMob(GameObject object) {
        DummyMob mob = new DummyMob(object, 100);
        object.getComponents().add(mob);
        return mob;
    }

    private GameObject object(GameContext context, Master master, Vector3 position) {
        GameObject object = new GameObject(master, PrefabType.MiniRock, new Vector3(position), context);
        object.setStatus(Status.Idle);
        return object;
    }
}
