package com.wordonline.server.game.service.pve;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveInstallObject;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PveScenarioInstallerTest {

    // The real GameContext.createGameObject() runs the prefab initializer synchronously before
    // the GameObject constructor returns (see ObjectsInfoDtoBuilder.createGameObject); a mocked
    // GameContext is a no-op, so this stub reproduces just enough of that for a Mob-bearing prefab
    // without wiring real Parameters/DatabaseMagicParser.
    private GameContext gameContextThatAttachesAMob(int prefabMaxHp) {
        GameContext gameContext = mock(GameContext.class);
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            gameObject.addComponent(new TestMob(gameObject, prefabMaxHp));
            gameObject.flushComponents();
            gameObject.setStatus(com.wordonline.server.game.dto.Status.Idle);
            return null;
        }).when(gameContext).createGameObject(any());
        return gameContext;
    }

    @Test
    void installOneRegistersTheObjectUnderItsInstallerId() {
        GameContext gameContext = mock(GameContext.class);
        PveScenarioInstaller installer = new PveScenarioInstaller();

        PveInstallObject spec = new PveInstallObject("boss", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null);
        GameObject created = installer.installOne(spec, gameContext);

        assertThat(installer.getRuntime().getInstalledObjectId("boss")).isEqualTo(created.getId());
    }

    @Test
    void installLeavesHpUntouchedWhenMaxHpIsNull() {
        GameContext gameContext = gameContextThatAttachesAMob(100);
        PveScenarioInstaller installer = new PveScenarioInstaller();

        PveInstallObject spec = new PveInstallObject("boss", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null);
        GameObject created = installer.installOne(spec, gameContext);

        Mob mob = created.getComponent(Mob.class);
        assertThat(mob.getMaxHp()).isEqualTo(100);
        assertThat(mob.getHp()).isEqualTo(100);
    }

    @Test
    void installOverridesMaxHpAndCurrentHpWhenGiven() {
        GameContext gameContext = gameContextThatAttachesAMob(100);
        PveScenarioInstaller installer = new PveScenarioInstaller();

        PveInstallObject spec = new PveInstallObject("boss", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, 500);
        GameObject created = installer.installOne(spec, gameContext);

        Mob mob = created.getComponent(Mob.class);
        assertThat(mob.getMaxHp()).isEqualTo(500);
        assertThat(mob.getHp()).isEqualTo(500);
    }

    @Test
    void installInstallsEveryInstallerInTheList() {
        GameContext gameContext = mock(GameContext.class);
        PveScenarioInstaller installer = new PveScenarioInstaller();

        installer.install(List.of(
                new PveInstallObject("a", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null),
                new PveInstallObject("b", PrefabType.ZapMouse, Master.RightPlayer, Vector3.ZERO, null)
        ), gameContext);

        assertThat(installer.getRuntime().getInstalledObjectId("a")).isNotEqualTo(-1);
        assertThat(installer.getRuntime().getInstalledObjectId("b")).isNotEqualTo(-1);
        assertThat(installer.getRuntime().getInstalledObjectId("unknown")).isEqualTo(-1);
    }

    @Test
    void getInstalledObjectReturnsNullWhenTheObjectWasNeverInstalled() {
        GameContext gameContext = mock(GameContext.class);
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(List.of(), gameContext);

        assertThat(installer.getInstalledObject(gameContext, "boss")).isNull();
    }

    private static class TestMob extends Mob {
        private TestMob(GameObject gameObject, int maxHp) {
            super(gameObject, maxHp, 0);
        }

        @Override
        public void onDeath() {
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }

    // The prefab initializer adds the boss mob only when the object starts, a frame after install,
    // so max_hp has to wait for it instead of being dropped.
    @Test
    void maxHpOverrideWaitsForTheBossMobToAppear() {
        com.wordonline.server.game.service.GameContext context =
                org.mockito.Mockito.mock(com.wordonline.server.game.service.GameContext.class);
        PveScenarioInstaller installer = new PveScenarioInstaller();
        installer.install(java.util.List.of(), context);
        com.wordonline.server.game.domain.object.GameObject boss = installer.installOne(
                new com.wordonline.server.game.domain.pve.PveInstallObject("boss",
                        com.wordonline.server.game.domain.object.prefab.PrefabType.PveNatureSlimeNest,
                        com.wordonline.server.game.dto.Master.RightPlayer,
                        com.wordonline.server.game.domain.object.Vector3.ZERO, 600),
                context);

        var bossMob = new com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PVEBossMob(
                boss, 1000, 0f, 0, 2.5f, 7f, java.util.List.of());
        boss.addComponent(bossMob);
        boss.addComponent(new com.wordonline.server.game.domain.object.component.TimedSelfDestroyer(boss, 60));
        boss.flushComponents();
        installer.finishPendingSetup();
        // Still initializing: nothing is applied yet.
        org.assertj.core.api.Assertions.assertThat(bossMob.getMaxHp()).isEqualTo(1000);

        boss.setStatus(com.wordonline.server.game.dto.Status.Idle);

        installer.finishPendingSetup();
        org.assertj.core.api.Assertions.assertThat(bossMob.getMaxHp()).isEqualTo(600);
        org.assertj.core.api.Assertions.assertThat(bossMob.getHp()).isEqualTo(600);
        // A scenario structure never expires on its own.
        boss.flushComponents();
        org.assertj.core.api.Assertions.assertThat(
                boss.getComponent(com.wordonline.server.game.domain.object.component.TimedSelfDestroyer.class)).isNull();
    }
}
