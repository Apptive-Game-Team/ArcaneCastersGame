package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.CraterShell;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CraterCarrierMobTest {
    private final GameContext context = mock(GameContext.class);
    private final GameObject self = object(Master.LeftPlayer, PrefabType.CraterCarrier, 1f);
    private final GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, 5f);
    private final TestCarrier mob = new TestCarrier(self);

    private void prepare() {
        when(context.getDeltaTime()).thenReturn(0.1f);
        mob.detector = mock(com.wordonline.server.game.domain.object.component.mob.detector.Detector.class);
        self.getComponents().add(new RigidBody(self, 4));
        mob.target = enemy;
        mob.targetRadius = 0.35f;
        doAnswer(invocation -> {
            GameObject shell = invocation.getArgument(0);
            if (shell.getType() == PrefabType.CraterShell) {
                shell.getComponents().add(new CraterShell(shell, 6f, 1.4f));
            }
            return null;
        }).when(context).createGameObject(any());
        mob.setState(mob.new AttackState());
        clearInvocations(context);
    }

    @Test
    void deploysBeforeFirstShotThenKeepsTheNormalAttackCadenceAndRestoresPhase() {
        prepare();
        tick(11);
        verify(context, never()).createGameObject(any());
        assertThat(self.getStatus()).isEqualTo(Status.Deploying);
        tick(2);
        verify(context, times(1)).createGameObject(argThat(o -> o.getType() == PrefabType.CraterShell));
        assertThat(self.getStatus()).isEqualTo(Status.Deployed);
        self.setStatus(Status.Damaged);
        tick(1);
        assertThat(self.getStatus()).isEqualTo(Status.Deployed);
        tick(27);
        verify(context, times(2)).createGameObject(argThat(o -> o.getType() == PrefabType.CraterShell));
    }

    @Test
    void targetLossDuringSetupCancelsShotAndRepeatedMoveRequestsCannotSkipOrResetPacking() {
        prepare();
        tick(3);
        enemy.destroy();
        tick(1);
        assertThat(self.getStatus()).isEqualTo(Status.Recovering);
        for (int i = 0; i < 8; i++) {
            mob.setState(mob.new MoveState());
            tick(1);
            assertThat(self.getStatus()).isEqualTo(Status.Recovering);
        }
        tick(2);
        assertThat(self.getStatus()).isEqualTo(Status.Idle);
        verify(context, never()).createGameObject(any());
    }

    @Test
    void leavingAttackRangeRequiresPackingBeforeWalking() {
        prepare();
        tick(13);
        enemy.setPosition(new Vector3(17, 0, 0));
        tick(1);
        assertThat(self.getStatus()).isEqualTo(Status.Recovering);
        assertThat(self.getComponent(RigidBody.class).getVelocity().distance(Vector3.ZERO)).isZero();
    }

    @Test
    void stunBlocksDirectiveRequestsAndStillRequiresPackingOnExpiry() {
        prepare();
        tick(3);
        mob.setStun(0.5f);
        for (int i = 0; i < 4; i++) {
            mob.setState(mob.new MoveState());
            tick(1);
            assertThat(mob.stateName()).isEqualTo("CarrierStunState");
        }
        tick(3);
        assertThat(self.getStatus()).isEqualTo(Status.Recovering);
        tick(10);
        assertThat(self.getStatus()).isEqualTo(Status.Idle);
        verify(context, never()).createGameObject(any());
    }

    @Test
    void ownershipResetAlsoPacksInsteadOfResumingAnOldTarget() {
        prepare();
        tick(3);
        mob.start(); // GameObject.setMaster restarts components
        assertThat(self.getStatus()).isEqualTo(Status.Recovering);
        tick(10);
        verify(context, never()).createGameObject(any());
    }

    @Test
    void lethalDelayedDamageCannotLaunchAShotOnTheSameUpdate() {
        prepare();
        tick(11);
        self.destroy();
        tick(2);
        verify(context, never()).createGameObject(any());
    }

    private void tick(int count) {
        for (int i = 0; i < count; i++) mob.advanceState();
    }

    private static class TestCarrier extends CraterCarrierMob {
        TestCarrier(GameObject object) {
            super(object, 440, 0.65f, 150, 2.6f, 7f, 1.2f, 0.9f);
        }
        void advanceState() { currentState.onUpdate(); }
        String stateName() { return currentState.getClass().getSimpleName(); }
    }

    private GameObject object(Master master, PrefabType type, float x) {
        GameObject object = new GameObject(master, type, new Vector3(x, 0, 5), context);
        object.addCollider(new CircleCollider(object, 0.35f, false));
        object.setStatus(Status.Idle);
        return object;
    }
}
