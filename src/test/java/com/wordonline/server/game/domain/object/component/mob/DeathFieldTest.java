package com.wordonline.server.game.domain.object.component.mob;

import java.util.Set;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeathFieldTest {

    @Test
    void combatDeathLaysTheElementFieldOnTheGroundForTheOwner() {
        GameContext gameContext = mock(GameContext.class);
        GameObject owner = owner(gameContext, new Vector3(2f, 3f, 4f), Set.of(ElementType.FIRE));
        TestMob mob = new TestMob(owner, 5);

        mob.applyDamage(new AttackInfo(5, ElementType.NONE));

        GameObject field = lastCreated(gameContext);
        assertThat(field.getType()).isEqualTo(PrefabType.FireField);
        assertThat(field.getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(field.getPosition()).isEqualTo(new Vector3(2f, 0f, 4f));
    }

    @RepeatedTest(20)
    void picksOneOfTheFieldElementsWhenTheBodyHasSeveral() {
        GameContext gameContext = mock(GameContext.class);
        GameObject owner = owner(gameContext, Vector3.ZERO,
                Set.of(ElementType.WATER, ElementType.WIND, ElementType.LIGHTNING));
        TestMob mob = new TestMob(owner, 1);

        mob.applyDamage(new AttackInfo(1, ElementType.NONE));

        // owner 자신 + 필드 한 개
        verify(gameContext, times(2)).createGameObject(any(GameObject.class));
        assertThat(lastCreated(gameContext).getType())
                .isIn(PrefabType.WaterField, PrefabType.ElectricField);
    }

    @Test
    void elementsWithoutAFieldLeaveNothing() {
        GameContext gameContext = mock(GameContext.class);
        GameObject owner = owner(gameContext, Vector3.ZERO, Set.of(ElementType.ROCK, ElementType.WIND));
        TestMob mob = new TestMob(owner, 1);

        mob.applyDamage(new AttackInfo(1, ElementType.NONE));

        verify(gameContext, times(1)).createGameObject(any(GameObject.class));
    }

    @Test
    void directDestroyLeavesNothing() {
        GameContext gameContext = mock(GameContext.class);
        GameObject owner = owner(gameContext, Vector3.ZERO, Set.of(ElementType.FIRE));
        new TestMob(owner, 1);

        owner.destroy();

        verify(gameContext, times(1)).createGameObject(any(GameObject.class));
    }

    private GameObject owner(GameContext gameContext, Vector3 position, Set<ElementType> elements) {
        GameObject owner = new GameObject(Master.LeftPlayer, PrefabType.ZapMouse, position, gameContext);
        owner.setStatus(Status.Idle);
        owner.setElement(elements);
        return owner;
    }

    private GameObject lastCreated(GameContext gameContext) {
        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, org.mockito.Mockito.atLeastOnce()).createGameObject(created.capture());
        return created.getValue();
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
