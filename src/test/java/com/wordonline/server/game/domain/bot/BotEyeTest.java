package com.wordonline.server.game.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

// The point of the snapshot is that nothing the loop thread does after it is taken can reach the
// bot executor thread. These tests fail if any part of it goes back to referencing live state.
class BotEyeTest {

    private final PlayerData leftPlayerData = new PlayerData(null);
    private final PlayerData rightPlayerData = new PlayerData(null);
    private final GameSessionData sessionData = new GameSessionData(leftPlayerData, rightPlayerData);

    private GameObject gameObject(Master master, PrefabType type, Vector3 position) {
        GameObject gameObject = mock(GameObject.class);
        when(gameObject.getMaster()).thenReturn(master);
        when(gameObject.getType()).thenReturn(type);
        when(gameObject.getPosition()).thenReturn(position);
        when(gameObject.isActive()).thenReturn(true);
        return gameObject;
    }

    @Test
    void copiesWhatTheSideCanSee() {
        leftPlayerData.mana = 7;
        leftPlayerData.cards.add(34L);
        sessionData.gameObjects.add(gameObject(Master.RightPlayer, PrefabType.Player, new Vector3(1, 0, 2)));

        BotEye eye = BotEye.observe(sessionData, Master.LeftPlayer);

        assertThat(eye.mana()).isEqualTo(7);
        assertThat(eye.cardList()).containsExactly(34L);
        assertThat(eye.gameObjectList()).singleElement().satisfies(visible -> {
            assertThat(visible.master()).isEqualTo(Master.RightPlayer);
            assertThat(visible.type()).isEqualTo(PrefabType.Player);
            assertThat(visible.position()).isEqualTo(new Vector3(1, 0, 2));
            assertThat(visible.mob()).isFalse();
        });
    }

    // Vector3 is mutable and the loop thread moves objects every frame, so the snapshot has to hold
    // its own copy rather than the live instance.
    @Test
    void keepsThePositionTheObjectHadWhenObserved() {
        Vector3 livePosition = new Vector3(1, 0, 2);
        sessionData.gameObjects.add(gameObject(Master.RightPlayer, PrefabType.Player, livePosition));

        BotEye eye = BotEye.observe(sessionData, Master.LeftPlayer);
        livePosition.setX(99);

        assertThat(eye.gameObjectList().getFirst().position()).isEqualTo(new Vector3(1, 0, 2));
    }

    @Test
    void isNotAffectedByLaterChangesToTheWorldOrTheHand() {
        leftPlayerData.mana = 7;
        leftPlayerData.cards.add(34L);
        sessionData.gameObjects.add(gameObject(Master.RightPlayer, PrefabType.Player, new Vector3(1, 0, 2)));

        BotEye eye = BotEye.observe(sessionData, Master.LeftPlayer);

        leftPlayerData.mana = 0;
        leftPlayerData.cards.clear();
        sessionData.gameObjects.clear();

        assertThat(eye.mana()).isEqualTo(7);
        assertThat(eye.cardList()).containsExactly(34L);
        assertThat(eye.gameObjectList()).hasSize(1);
    }

    @Test
    void handsOutListsTheBotThreadCannotWriteTo() {
        BotEye eye = BotEye.observe(sessionData, Master.LeftPlayer);

        assertThatThrownBy(() -> eye.cardList().add(34L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> eye.gameObjectList().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readsTheManaAndHandOfTheSideItIsAskedAbout() {
        leftPlayerData.mana = 1;
        rightPlayerData.mana = 9;
        rightPlayerData.cards.add(12L);

        BotEye eye = BotEye.observe(sessionData, Master.RightPlayer);

        assertThat(eye.mana()).isEqualTo(9);
        assertThat(eye.cardList()).containsExactly(12L);
    }
}
