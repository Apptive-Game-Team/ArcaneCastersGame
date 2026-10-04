package com.wordonline.server.game.service.system;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.pve.PveScenarioRules;
import com.wordonline.server.game.domain.pve.PveWinCondition;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.pve.PveObjectiveDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.PveResultChecker;
import com.wordonline.server.game.service.pve.PveScenarioInstaller;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PveObjectiveSystemTest {

    private final List<GameObject> world = new ArrayList<>();
    private final GameContext gameContext = mock(GameContext.class);
    private final SessionObject sessionObject = mock(SessionObject.class);
    private final PveScenarioInstaller.RuntimeState runtime = new PveScenarioInstaller.RuntimeState();
    private final PveResultChecker checker;
    private final PveObjectiveSystem system;

    PveObjectiveSystemTest() {
        when(sessionObject.getGameContext()).thenReturn(gameContext);
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(-1L);
        when(gameContext.getSessionObject()).thenReturn(sessionObject);
        when(gameContext.getGameObjects()).thenReturn(world);
        checker = new PveResultChecker(sessionObject);
        checker.setRuntime(runtime);
        system = new PveObjectiveSystem(checker);
    }

    private GameObject install(String installerId) {
        GameObject gameObject = new GameObject(Master.RightPlayer, PrefabType.ZapMouse, Vector3.ZERO, gameContext);
        world.add(gameObject);
        runtime.register(installerId, gameObject);
        return gameObject;
    }

    private void updateAt(int frameNum) {
        when(gameContext.getFrameNum()).thenReturn(frameNum);
        system.update(gameContext);
    }

    private List<PveObjectiveDto> sentToLeft() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(sessionObject, org.mockito.Mockito.atLeast(0)).sendFrameInfo(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        return captor.getAllValues().stream().map(PveObjectiveDto.class::cast).toList();
    }

    @Test
    void firstUpdateSendsToBothUsersWithTypeField() {
        checker.setObjectiveInstallerIds(List.of("a", "b"));
        checker.configureRules(PveScenarioRules.defaultRules());
        install("a");

        updateAt(0);

        verify(sessionObject).sendFrameInfo(org.mockito.ArgumentMatchers.eq(1L), any(PveObjectiveDto.class));
        verify(sessionObject).sendFrameInfo(org.mockito.ArgumentMatchers.eq(-1L), any(PveObjectiveDto.class));
        PveObjectiveDto dto = sentToLeft().get(0);
        assertThat(dto.type()).isEqualTo("pveObjective");
        assertThat(dto.winCondition()).isEqualTo(PveWinCondition.DestroyObjectives);
        assertThat(dto.surviveSeconds()).isZero();
        assertThat(dto.remainingSeconds()).isZero();
        assertThat(dto.objectivesTotal()).isEqualTo(2);
        // "b" is not installed yet and still counts.
        assertThat(dto.objectivesRemaining()).isEqualTo(2);
    }

    @Test
    void doesNotResendWithinTheResendWindowWhileNothingChanges() {
        checker.setObjectiveInstallerIds(List.of("a"));
        checker.configureRules(PveScenarioRules.defaultRules());
        install("a");

        updateAt(0);
        updateAt(1);
        updateAt(PveObjectiveSystem.RESEND_SECONDS * GameLoop.FPS - 1);

        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any());
        verify(sessionObject, never()).sendFrameInfo(org.mockito.ArgumentMatchers.eq(2L), any());
    }

    @Test
    void resendsTheSameValueEveryTwoSecondsSoALateClientLearnsIt() {
        checker.setObjectiveInstallerIds(List.of("a"));
        checker.configureRules(PveScenarioRules.defaultRules());
        install("a");
        int window = PveObjectiveSystem.RESEND_SECONDS * GameLoop.FPS;

        updateAt(0);
        updateAt(window - 1);
        updateAt(window);
        updateAt(window + 5);
        updateAt(2 * window);

        List<PveObjectiveDto> sent = sentToLeft();
        assertThat(sent).hasSize(3);
        assertThat(sent).extracting(PveObjectiveDto::objectivesRemaining).containsOnly(1);
    }

    @Test
    void resendsWhenAnObjectiveIsDestroyed() {
        checker.setObjectiveInstallerIds(List.of("a", "b"));
        checker.configureRules(PveScenarioRules.defaultRules());
        GameObject a = install("a");
        install("b");

        updateAt(0);
        a.destroy();
        world.remove(a);
        updateAt(1);
        updateAt(2);

        List<PveObjectiveDto> sent = sentToLeft();
        assertThat(sent).extracting(PveObjectiveDto::objectivesRemaining).containsExactly(2, 1);
        assertThat(sent.get(1).objectivesTotal()).isEqualTo(2);
    }

    @Test
    void surviveCountsDownRoundingUpAndResendsOncePerSecond() {
        checker.configureRules(new PveScenarioRules(PveWinCondition.Survive, 10));

        updateAt(0);
        updateAt(1);
        updateAt(GameLoop.FPS - 1);
        updateAt(GameLoop.FPS);
        updateAt(10 * GameLoop.FPS - 1);
        updateAt(10 * GameLoop.FPS);
        updateAt(10 * GameLoop.FPS + 10);

        List<PveObjectiveDto> sent = sentToLeft();
        assertThat(sent).extracting(PveObjectiveDto::remainingSeconds).containsExactly(10, 9, 1, 0);
        assertThat(sent).allSatisfy(dto -> {
            assertThat(dto.winCondition()).isEqualTo(PveWinCondition.Survive);
            assertThat(dto.surviveSeconds()).isEqualTo(10);
            assertThat(dto.objectivesTotal()).isZero();
            assertThat(dto.objectivesRemaining()).isZero();
        });
    }
}
