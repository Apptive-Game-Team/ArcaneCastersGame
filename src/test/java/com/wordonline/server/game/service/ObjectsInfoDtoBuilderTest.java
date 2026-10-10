package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.debug.Gizmo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.GaugeComponent;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.UpdatedObjectDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectsInfoDtoBuilderTest {

    private final GameContext gameContext = mock(GameContext.class);
    private final ObjectsInfoDtoBuilder builder = new ObjectsInfoDtoBuilder(gameContext);

    private GameObject gameObject(int id) {
        GameObject gameObject = mock(GameObject.class);
        when(gameObject.getId()).thenReturn(id);
        when(gameObject.getType()).thenReturn(PrefabType.FireShot);
        when(gameObject.getPosition()).thenReturn(Vector3.ZERO);
        when(gameObject.getStatus()).thenReturn(Status.Idle);
        when(gameObject.getMaster()).thenReturn(Master.LeftPlayer);
        when(gameObject.getEffects()).thenReturn(new ArrayList<>());
        when(gameObject.getGizmos()).thenReturn(new ArrayList<>());
        when(gameObject.getComponents(GaugeComponent.class)).thenReturn(List.of());
        return gameObject;
    }

    @Test
    void repeatedUpdatesInOneFrameCollapseIntoOneDtoCarryingTheLastState() {
        GameObject gameObject = gameObject(7);

        builder.updateGameObject(gameObject);

        when(gameObject.getPosition()).thenReturn(new Vector3(1f, 0f, 2f));
        builder.updateGameObject(gameObject);

        when(gameObject.getStatus()).thenReturn(Status.Move);
        when(gameObject.getMaster()).thenReturn(Master.RightPlayer);
        when(gameObject.getEffects()).thenReturn(new ArrayList<>(List.of(Effect.Burn)));
        when(gameObject.getPosition()).thenReturn(new Vector3(3f, 0f, 4f));
        builder.updateGameObject(gameObject);

        List<UpdatedObjectDto> updates = builder.getObjectsInfoDto().update();

        assertThat(updates).hasSize(1);
        UpdatedObjectDto dto = updates.get(0);
        assertThat(dto.getId()).isEqualTo(7);
        assertThat(dto.getPosition()).isEqualTo(new Vector3(3f, 0f, 4f));
        assertThat(dto.getStatus()).isEqualTo(Status.Move);
        assertThat(dto.getMaster()).isEqualTo(Master.RightPlayer);
        assertThat(dto.getEffects()).containsExactly(Effect.Burn);
    }

    @Test
    void updateListKeepsFirstUpdateOrderWhenObjectsAreUpdatedAgain() {
        GameObject first = gameObject(1);
        GameObject second = gameObject(2);
        GameObject third = gameObject(3);

        builder.updateGameObject(first);
        builder.updateGameObject(second);
        builder.updateGameObject(third);
        builder.updateGameObject(first);
        builder.updateGameObject(second);

        assertThat(builder.getObjectsInfoDto().update())
                .extracting(UpdatedObjectDto::getId)
                .containsExactly(1, 2, 3);
    }

    @Test
    void getObjectsInfoDtoResetsListAndIndexTogetherSoTheNextFrameStartsEmpty() {
        GameObject gameObject = gameObject(11);

        builder.updateGameObject(gameObject);
        ObjectsInfoDto firstFrame = builder.getObjectsInfoDto();

        assertThat(builder.getObjectsInfoDto().update()).isEmpty();

        when(gameObject.getPosition()).thenReturn(new Vector3(5f, 0f, 6f));
        builder.updateGameObject(gameObject);
        ObjectsInfoDto thirdFrame = builder.getObjectsInfoDto();

        assertThat(firstFrame.update()).hasSize(1);
        assertThat(thirdFrame.update()).hasSize(1);
        // the index must not hand the previous frame's DTO back, or that already sent
        // frame would keep mutating
        assertThat(thirdFrame.update().get(0)).isNotSameAs(firstFrame.update().get(0));
        assertThat(firstFrame.update().get(0).getPosition()).isEqualTo(Vector3.ZERO);
        assertThat(thirdFrame.update().get(0).getPosition()).isEqualTo(new Vector3(5f, 0f, 6f));
    }

    @Test
    void objectCreatedAndUpdatedInTheSameFrameAppearsOnceInEachList() {
        GameObject gameObject = gameObject(21);

        builder.createGameObject(gameObject);
        builder.updateGameObject(gameObject);
        when(gameObject.getStatus()).thenReturn(Status.Attack);
        builder.updateGameObject(gameObject);

        ObjectsInfoDto frame = builder.getObjectsInfoDto();

        verify(gameContext).addGameObject(gameObject);
        verify(gameObject).start();
        assertThat(frame.create()).hasSize(1);
        assertThat(frame.create().get(0).id()).isEqualTo(21);
        assertThat(frame.update()).hasSize(1);
        assertThat(frame.update().get(0).getId()).isEqualTo(21);
        assertThat(frame.update().get(0).getStatus()).isEqualTo(Status.Attack);
    }

    @Test
    void emptyEffectsAndGizmosAreNotCopiedButNonEmptyOnesAreSnapshotted() {
        GameObject gameObject = gameObject(31);
        List<Gizmo> gizmos = new ArrayList<>();
        List<Effect> effects = new ArrayList<>();
        when(gameObject.getGizmos()).thenReturn(gizmos);
        when(gameObject.getEffects()).thenReturn(effects);

        builder.createGameObject(gameObject);
        builder.updateGameObject(gameObject);
        builder.updateGameObject(gameObject);
        ObjectsInfoDto emptyFrame = builder.getObjectsInfoDto();

        assertThat(emptyFrame.create().get(0).gizmos()).isSameAs(List.of());
        assertThat(emptyFrame.update().get(0).getEffects()).isSameAs(List.of());

        gizmos.add(Gizmo.circle(Vector3.ZERO, 1f, GizmoCategory.Collider));
        effects.add(Effect.Wet);

        builder.createGameObject(gameObject);
        builder.updateGameObject(gameObject);
        builder.updateGameObject(gameObject);
        ObjectsInfoDto filledFrame = builder.getObjectsInfoDto();

        assertThat(filledFrame.create().get(0).gizmos()).containsExactlyElementsOf(gizmos);
        assertThat(filledFrame.update().get(0).getEffects()).containsExactly(Effect.Wet);
        // the copy must not alias the live list
        effects.clear();
        assertThat(filledFrame.update().get(0).getEffects()).containsExactly(Effect.Wet);
    }
}
