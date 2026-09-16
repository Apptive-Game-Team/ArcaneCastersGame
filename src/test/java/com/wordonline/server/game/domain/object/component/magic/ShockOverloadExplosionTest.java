package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShockOverloadExplosionTest {

    private GameObject source;
    private GameObject markedTarget;
    private Damageable markedDamageable;
    private GameContext gameContext;
    private Physics physics;
    private ObjectsInfoDtoBuilder dtoBuilder;
    private ShockOverloadExplosion explosion;

    @BeforeEach
    void setUp() {
        source = mock(GameObject.class);
        markedTarget = mock(GameObject.class);
        markedDamageable = mock(Damageable.class);
        gameContext = mock(GameContext.class);
        physics = mock(Physics.class);
        dtoBuilder = mock(ObjectsInfoDtoBuilder.class);

        Element element = new Element();
        element.addNative(ElementType.LIGHTNING);

        when(source.getGameContext()).thenReturn(gameContext);
        when(source.getMaster()).thenReturn(Master.LeftPlayer);
        when(source.getElement()).thenReturn(element);
        when(source.getId()).thenReturn(101);
        when(gameContext.getDeltaTime()).thenReturn(0.5f);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(markedTarget.getMaster()).thenReturn(Master.RightPlayer);
        when(markedTarget.getComponents(Damageable.class)).thenReturn(List.of(markedDamageable));
        when(gameContext.overlapSphereAll(source, 4f)).thenReturn(List.of(markedTarget));

        explosion = new ShockOverloadExplosion(source, 10, 4f);
    }

    @Test
    void primaryExplosionMarksExactlyTheEnemiesItDamagesInsteadOfStThem() {
        explosion.update();

        verify(markedDamageable).onDamaged(new AttackInfo(10, ElementType.LIGHTNING).withAttacker(source));
        verify(markedTarget).addEffect(Effect.Shock);
        verify(source, never()).destroy();
    }

    @Test
    void primaryExplosionSkipsAllies() {
        GameObject ally = mock(GameObject.class);
        Damageable allyDamageable = mock(Damageable.class);
        when(ally.getMaster()).thenReturn(Master.LeftPlayer);
        when(ally.getComponents(Damageable.class)).thenReturn(List.of(allyDamageable));
        when(gameContext.overlapSphereAll(source, 4f)).thenReturn(List.of(ally, markedTarget));

        explosion.update();

        verifyNoInteractions(allyDamageable);
        verify(ally, never()).addEffect(any());
        verify(markedDamageable).onDamaged(any(AttackInfo.class));
    }

    @Test
    void secondaryExplosionUsesCurrentTargetPositionAndReducedDamageAndRadius() {
        GameObject nearbyEnemy = mock(GameObject.class);
        Damageable nearbyDamageable = mock(Damageable.class);
        Vector3 movedPosition = new Vector3(7f, 0f, 3f);
        when(markedTarget.getPosition()).thenReturn(movedPosition);
        when(nearbyEnemy.getMaster()).thenReturn(Master.RightPlayer);
        when(nearbyEnemy.getComponents(Damageable.class)).thenReturn(List.of(nearbyDamageable));
        when(physics.overlapSphereAll(movedPosition, 2f)).thenReturn(List.of(nearbyEnemy));

        runThroughSecondaryDelay();

        verify(markedTarget).removeEffect(Effect.Shock);
        verify(dtoBuilder).createProjection(
                movedPosition,
                movedPosition,
                "ShockOverloadSecondary",
                0.8f
        );
        ArgumentCaptor<AttackInfo> attack = ArgumentCaptor.forClass(AttackInfo.class);
        verify(nearbyDamageable).onDamaged(attack.capture());
        assertThat(attack.getValue().getDamage()).isEqualTo(5);
        assertThat(attack.getValue().getElement()).containsExactly(ElementType.LIGHTNING);
        assertThat(attack.getValue().getAttackerId()).isEqualTo(101);
        verify(source).destroy();
    }

    @Test
    void destroyedMarkedTargetDoesNotCreateASecondaryExplosion() {
        when(markedTarget.isDestroyed()).thenReturn(true);

        runThroughSecondaryDelay();

        verify(markedTarget).removeEffect(Effect.Shock);
        verifyNoInteractions(physics);
        verifyNoInteractions(dtoBuilder);
        verify(source).destroy();
    }

    private void runThroughSecondaryDelay() {
        explosion.update();
        explosion.update();
        explosion.update();
    }
}
