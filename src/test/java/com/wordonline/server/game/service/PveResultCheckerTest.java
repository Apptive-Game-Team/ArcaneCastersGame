package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PveResultCheckerTest {

    private final List<GameObject> world = new ArrayList<>();
    private final GameContext gameContext = mock(GameContext.class);
    private final SessionObject sessionObject = mock(SessionObject.class);

    private PveResultChecker newChecker() {
        when(sessionObject.getGameContext()).thenReturn(gameContext);
        when(gameContext.getGameObjects()).thenReturn(world);
        return new PveResultChecker(sessionObject);
    }

    private GameObject boss() {
        GameObject gameObject = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, gameContext);
        world.add(gameObject);
        return gameObject;
    }

    @Test
    void leftPlayerDeathFailsRegardlessOfObjectives() {
        PveResultChecker checker = newChecker();
        checker.setObjectiveInstallerIds(List.of("boss"));
        checker.setRuntime(new PveScenarioInstaller.RuntimeState());
        checker.setLoser(Master.LeftPlayer);

        assertThat(checker.checkResult()).isTrue();
        assertThat(checker.getLoser()).isEqualTo(Master.LeftPlayer);
    }

    @Test
    void destroyObjectivesClearsOnceEveryObjectiveIsTerminal() {
        PveResultChecker checker = newChecker();
        PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
        GameObject boss = boss();
        runtime.register("boss", boss);

        checker.setObjectiveInstallerIds(List.of("boss"));
        checker.setRuntime(runtime);
        checker.configureRules(PveScenarioRules.defaultRules());

        assertThat(checker.checkResult()).isFalse();

        boss.destroy();
        world.remove(boss);

        assertThat(checker.checkResult()).isTrue();
        assertThat(checker.getLoser()).isEqualTo(Master.RightPlayer);
    }

    @Test
    void anObjectiveNeverInstalledBlocksTheWin() {
        PveResultChecker checker = newChecker();
        PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
        GameObject boss = boss();
        runtime.register("boss", boss);
        boss.destroy();
        world.remove(boss);

        // "extra" is a second objective that no InstallObject action has installed yet.
        checker.setObjectiveInstallerIds(List.of("boss", "extra"));
        checker.setRuntime(runtime);
        checker.configureRules(PveScenarioRules.defaultRules());

        assertThat(checker.checkResult()).isFalse();

        // Once it is installed and destroyed too, the win goes through.
        GameObject extra = boss();
        runtime.register("extra", extra);
        extra.destroy();
        world.remove(extra);

        assertThat(checker.checkResult()).isTrue();
    }

    @Test
    void surviveClearsOnceTheFrameThresholdIsReachedWhileAnObjectiveIsNeverInstalled() {
        PveResultChecker checker = newChecker();
        checker.setObjectiveInstallerIds(List.of("never-installed"));
        checker.setRuntime(new PveScenarioInstaller.RuntimeState());
        checker.configureRules(new PveScenarioRules(PveWinCondition.Survive, 10));

        when(gameContext.getFrameNum()).thenReturn(10 * 20 - 1);
        assertThat(checker.checkResult()).isFalse();

        when(gameContext.getFrameNum()).thenReturn(10 * 20);
        assertThat(checker.checkResult()).isTrue();
        assertThat(checker.getLoser()).isEqualTo(Master.RightPlayer);
    }

    @Test
    void surviveClearsBeforeTheTimerOnceEveryObjectiveIsTerminal() {
        PveResultChecker checker = newChecker();
        PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
        GameObject boss = boss();
        runtime.register("boss", boss);

        checker.setObjectiveInstallerIds(List.of("boss"));
        checker.setRuntime(runtime);
        checker.configureRules(new PveScenarioRules(PveWinCondition.Survive, 90));
        when(gameContext.getFrameNum()).thenReturn(5 * 20);

        assertThat(checker.checkResult()).isFalse();

        boss.destroy();
        world.remove(boss);

        assertThat(checker.checkResult()).isTrue();
        assertThat(checker.getLoser()).isEqualTo(Master.RightPlayer);
    }

    @Test
    void surviveDoesNotClearBeforeTheTimerWhileAnObjectiveIsAlive() {
        PveResultChecker checker = newChecker();
        PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
        GameObject boss = boss();
        GameObject structure = boss();
        runtime.register("boss", boss);
        runtime.register("structure", structure);
        boss.destroy();
        world.remove(boss);

        checker.setObjectiveInstallerIds(List.of("boss", "structure"));
        checker.setRuntime(runtime);
        checker.configureRules(new PveScenarioRules(PveWinCondition.Survive, 90));

        when(gameContext.getFrameNum()).thenReturn(90 * 20 - 1);
        assertThat(checker.checkResult()).isFalse();

        when(gameContext.getFrameNum()).thenReturn(90 * 20);
        assertThat(checker.checkResult()).isTrue();
    }

    @Test
    void surviveWithoutObjectivesClearsOnlyOnTheTimer() {
        PveResultChecker checker = newChecker();
        checker.setObjectiveInstallerIds(List.of());
        checker.setRuntime(new PveScenarioInstaller.RuntimeState());
        checker.configureRules(new PveScenarioRules(PveWinCondition.Survive, 10));

        when(gameContext.getFrameNum()).thenReturn(10 * 20 - 1);
        assertThat(checker.checkResult()).isFalse();

        when(gameContext.getFrameNum()).thenReturn(10 * 20);
        assertThat(checker.checkResult()).isTrue();
        assertThat(checker.getLoser()).isEqualTo(Master.RightPlayer);
    }

    // A freshly installed object joins gameContext.getGameObjects() only on the next frame.
    // Being absent from that list at frame 0 must not count as destroyed; it once cleared
    // every scenario the instant the match started.
    @Test
    void anObjectiveNotYetInTheWorldListDoesNotClear() {
        PveResultChecker checker = newChecker();
        PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
        GameObject boss = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, gameContext);
        runtime.register("boss", boss);

        checker.setObjectiveInstallerIds(List.of("boss"));
        checker.setRuntime(runtime);
        checker.configureRules(PveScenarioRules.defaultRules());

        assertThat(world).doesNotContain(boss);
        assertThat(checker.checkResult()).isFalse();
    }
}
