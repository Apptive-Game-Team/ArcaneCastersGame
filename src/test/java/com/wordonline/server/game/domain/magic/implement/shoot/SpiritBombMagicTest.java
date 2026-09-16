package com.wordonline.server.game.domain.magic.implement.shoot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.SpiritBombChannel;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;

class SpiritBombMagicTest {

    @Test
    void drainsOnlyHealthAboveHalfAndStartsChannelWithAbsorbedDamage() {
        GameContext context = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        when(context.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);

        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject healthyAlly = object(Master.LeftPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 0f), context);
        DummyMob healthyMob = new DummyMob(healthyAlly, 100);
        healthyAlly.getComponents().add(healthyMob);
        healthyMob.applyDamage(new AttackInfo(20, ElementType.NONE));

        GameObject lowAlly = object(Master.LeftPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 1f), context);
        DummyMob lowMob = new DummyMob(lowAlly, 100);
        lowAlly.getComponents().add(lowMob);
        lowMob.applyDamage(new AttackInfo(60, ElementType.NONE));

        when(context.findPlayerGameObject(Master.LeftPlayer)).thenReturn(Optional.of(player));
        when(context.getActiveGameObjects()).thenReturn(List.of(player, healthyAlly, lowAlly));

        new SpiritBombMagic().run(
                context,
                Master.LeftPlayer,
                Vector3.ZERO,
                new Vector3(6f, 0f, 0f)
        );

        assertThat(healthyMob.getHp()).isEqualTo(50);
        assertThat(lowMob.getHp()).isEqualTo(40);
        assertThat(player.getComponentsToAdd()).anyMatch(SpiritBombChannel.class::isInstance);
        verify(dtoBuilder).createProjection(
                healthyAlly,
                player,
                SpiritBombMagic.ABSORB_PROJECTILE,
                SpiritBombMagic.ABSORB_PROJECTILE_DURATION
        );
    }

    private GameObject object(Master master, PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
