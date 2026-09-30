package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RockDeathRemnantTest {

    @Test
    void lethalCombatDamageCreatesOneOwnedRockRemnantAtCopiedDeathPosition() {
        GameContext gameContext = mock(GameContext.class);
        Vector3 deathPosition = new Vector3(3f, 2f, 4f);
        GameObject deadRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.RockGolem,
                deathPosition,
                gameContext
        );
        deadRock.setStatus(Status.Idle);
        deadRock.setElement(ElementType.ROCK);
        deadRock.addComponent(new RockDeathRemnant(deadRock));
        deadRock.flushComponents();
        TestMob mob = new TestMob(deadRock, 10);
        clearInvocations(gameContext);

        mob.applyDamage(new AttackInfo(10, ElementType.NONE));

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        GameObject remnant = created.getValue();
        assertThat(remnant.getType()).isEqualTo(PrefabType.RockRemnant);
        assertThat(remnant.getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(remnant.getPosition()).isEqualTo(deathPosition);
        assertThat(remnant.getPosition()).isNotSameAs(deathPosition);
        assertThat(remnant.getGameContext()).isSameAs(gameContext);
    }

    @Test
    void repeatedCombatDeathCallbackCreatesOnlyOneRockRemnant() {
        GameContext gameContext = mock(GameContext.class);
        GameObject deadRock = new GameObject(
                Master.RightPlayer,
                PrefabType.RockSlime,
                Vector3.ZERO,
                gameContext
        );
        RockDeathRemnant remnant = new RockDeathRemnant(deadRock);
        clearInvocations(gameContext);

        remnant.onCombatDeath();
        remnant.onCombatDeath();

        verify(gameContext, times(1)).createGameObject(any(GameObject.class));
    }

    @Test
    void mediumSourceCreatesMediumRockRemnant() {
        GameContext gameContext = mock(GameContext.class);
        GameObject deadRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.RockGolem,
                Vector3.ZERO,
                gameContext
        );
        RockDeathRemnant remnant = new RockDeathRemnant(deadRock, RockRemnantSize.MEDIUM);
        clearInvocations(gameContext);

        remnant.onCombatDeath();

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(PrefabType.MediumRockRemnant);
    }

    @Test
    void directDestroyDoesNotCreateRockRemnant() {
        GameContext gameContext = mock(GameContext.class);
        GameObject deadRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.RockGolem,
                Vector3.ZERO,
                gameContext
        );
        deadRock.addComponent(new RockDeathRemnant(deadRock));
        deadRock.flushComponents();
        clearInvocations(gameContext);

        deadRock.destroy();

        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void outOfBoundsDestroyDoesNotCreateRockRemnant() {
        GameContext gameContext = mock(GameContext.class);
        GameObject deadRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.RockGolem,
                Vector3.ZERO,
                gameContext
        );
        deadRock.addComponent(new RockDeathRemnant(deadRock));
        deadRock.flushComponents();
        clearInvocations(gameContext);

        deadRock.setPosition(new Vector3(10_000f, 0f, 10_000f));

        verify(gameContext, never()).createGameObject(any(GameObject.class));
        assertThat(deadRock.getStatus()).isEqualTo(Status.Destroyed);
    }

    private static class TestMob extends Mob {
        private TestMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp, 1f);
        }

        @Override
        public void onDeath() {
            gameObject.destroy();
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }
}
