package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrassSpreadTest {

    private static final float ATTACK_INTERVAL_SEC = 2f;
    private static final float RADIUS = 5f;
    private static final int QUANTITY_PER_RING = 4;
    private static final float TICK_DELTA_SEC = 0.5f;
    private static final float LEAF_FIELD_RADIUS = 0.5f;
    // leaf_field.duration, mirrored here so the freeze tests can drive a real TimedSelfDestroyer.
    private static final float LEAF_FIELD_DURATION_SEC = 3f;
    // Mirrors GrassSpread's private RING_SPACING so this test can compute the exact position of a
    // given (ring, slot) independently, without duplicating GrassSpread's internals.
    private static final float RING_SPACING = 1.75f;

    @Test
    void spreadsExactlyOneLeafFieldPerAttackInterval() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(TICK_DELTA_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);

        // 1.5s across three ticks: short of the 2s interval, nothing spreads yet.
        grassSpread.update();
        grassSpread.update();
        grassSpread.update();
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        // The fourth tick reaches the 2s interval: exactly one field appears.
        grassSpread.update();
        verify(gameContext, times(1)).createGameObject(any(GameObject.class));

        // Three more ticks (1.5s) must not add a second field yet.
        grassSpread.update();
        grassSpread.update();
        grassSpread.update();
        verify(gameContext, times(1)).createGameObject(any(GameObject.class));

        // The next tick completes the second interval.
        grassSpread.update();
        verify(gameContext, times(2)).createGameObject(any(GameObject.class));
    }

    @Test
    void aStalledFrameCatchesUpInsteadOfDroppingSpreads() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        // one frame that took 3.5 intervals worth of time to complete
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC * 3.5f);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        grassSpread.update();

        verify(gameContext, times(3)).createGameObject(any(GameObject.class));
    }

    @Test
    void everySpreadPositionStaysWithinRadiusOfTheBuildingAsLeafField() {
        GameContext gameContext = mock(GameContext.class);
        Vector3 center = new Vector3(10f, 0f, -3f);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, center, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        for (int i = 0; i < 12; i++) {
            grassSpread.update();
        }

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(12)).createGameObject(spawned.capture());

        List<GameObject> fields = spawned.getAllValues();
        assertThat(fields).hasSize(12);
        for (GameObject field : fields) {
            assertThat(field.getType()).isEqualTo(PrefabType.LeafField);

            Vector3 position = field.getPosition();
            float dx = position.getX() - center.getX();
            float dz = position.getZ() - center.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            assertThat(distance).isLessThanOrEqualTo(RADIUS + 0.01);
        }
    }

    @Test
    void stopsSpreadingOnceTheBuildingIsDestroyed() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        grassSpread.update();
        verify(gameContext, times(1)).createGameObject(any(GameObject.class));

        building.destroy();
        clearInvocations(gameContext);

        grassSpread.update();
        grassSpread.update();
        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void fillsInnermostRingFirstInSlotOrder() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        int ringCount = ringCount(RADIUS);
        for (int i = 0; i < QUANTITY_PER_RING + 1; i++) {
            grassSpread.update();
        }

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(QUANTITY_PER_RING + 1)).createGameObject(spawned.capture());
        List<GameObject> fields = spawned.getAllValues();

        // Ring 0's four slots come first, in slot order...
        for (int slotIndex = 0; slotIndex < QUANTITY_PER_RING; slotIndex++) {
            Vector3 expected = ringSlotPosition(Vector3.ZERO, ringCount, 0, slotIndex);
            assertThat(fields.get(slotIndex).getPosition()).isEqualTo(expected);
        }
        // ...and only then does ring 1 start.
        Vector3 firstRing1Position = ringSlotPosition(Vector3.ZERO, ringCount, 1, 0);
        assertThat(fields.get(QUANTITY_PER_RING).getPosition()).isEqualTo(firstRing1Position);
    }

    @Test
    void plantedFieldsSurviveFarPastLeafFieldDurationWhileTheGeneratorLives() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        doAnswer(invocation -> {
            GameObject created = invocation.getArgument(0);
            created.getComponents().add(new TimedSelfDestroyer(created, LEAF_FIELD_DURATION_SEC));
            return null;
        }).when(gameContext).createGameObject(any(GameObject.class));
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        grassSpread.update(); // plants the field this tick, too late in the tick to be frozen yet

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        GameObject field = spawned.getValue();
        TimedSelfDestroyer selfDestroyer = field.getComponent(TimedSelfDestroyer.class);
        selfDestroyer.update(); // this tick's own advance: elapsedTime becomes 2s, short of the 3s duration

        // 30 more ticks of 2s apiece: 60s of simulated time, twenty times the 3s duration.
        for (int i = 0; i < 30; i++) {
            grassSpread.update(); // freezes the field's timer for this tick
            selfDestroyer.update(); // consumes the freeze: elapsedTime does not move
        }

        assertThat(field.isDestroyed()).isFalse();
    }

    @Test
    void fieldsOwnTimerResumesAndDestroysAfterTheGeneratorIsDestroyed() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        doAnswer(invocation -> {
            GameObject created = invocation.getArgument(0);
            created.getComponents().add(new TimedSelfDestroyer(created, LEAF_FIELD_DURATION_SEC));
            return null;
        }).when(gameContext).createGameObject(any(GameObject.class));
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        grassSpread.update();

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        GameObject field = spawned.getValue();
        TimedSelfDestroyer selfDestroyer = field.getComponent(TimedSelfDestroyer.class);
        selfDestroyer.update(); // elapsedTime = 2s, short of the 3s duration

        building.destroy();

        // ComponentUpdateSystem stops calling GrassSpread.update() once the building is
        // destroyed: no more freeze() calls reach this field, so its own timer resumes.
        selfDestroyer.update(); // elapsedTime = 4s, past the 3s duration

        assertThat(field.isDestroyed()).isTrue();
    }

    @Test
    void destroyedFieldFreesExactlyItsSlotAndTheNextSpreadRefillsIt() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        // radius == RING_SPACING keeps ringCount at 1, so every slot lives on one ring.
        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RING_SPACING, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        for (int i = 0; i < QUANTITY_PER_RING; i++) {
            grassSpread.update();
        }

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(QUANTITY_PER_RING)).createGameObject(spawned.capture());
        List<GameObject> fields = spawned.getAllValues();
        Vector3 slot2Position = fields.get(2).getPosition();

        fields.get(2).destroy();
        clearInvocations(gameContext);

        grassSpread.update();

        ArgumentCaptor<GameObject> refilled = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(refilled.capture());
        assertThat(refilled.getValue().getPosition()).isEqualTo(slot2Position);
    }

    @Test
    void skipsASlotCoveredByABurnEffectProviderAndPlantsItOnceItIsGone() {
        GameContext gameContext = mock(GameContext.class);
        GameObject building = new GameObject(Master.LeftPlayer, PrefabType.GrassGenerator, Vector3.ZERO, gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);

        int ringCount = ringCount(RADIUS);
        Vector3 slot0Position = ringSlotPosition(Vector3.ZERO, ringCount, 0, 0);
        Vector3 slot1Position = ringSlotPosition(Vector3.ZERO, ringCount, 0, 1);

        GameObject fireField = mock(GameObject.class);
        EffectProvider burnProvider = mock(EffectProvider.class);
        when(burnProvider.getEffect()).thenReturn(Effect.Burn);
        when(fireField.getComponents(EffectProvider.class)).thenReturn(List.of(burnProvider));
        when(gameContext.overlapSphereAll(eq(slot0Position), eq(LEAF_FIELD_RADIUS))).thenReturn(List.of(fireField));
        clearInvocations(gameContext);

        GrassSpread grassSpread = new GrassSpread(building, ATTACK_INTERVAL_SEC, RADIUS, QUANTITY_PER_RING, LEAF_FIELD_RADIUS);
        grassSpread.update(); // slot 0 is burning: skipped in favor of slot 1

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        assertThat(spawned.getValue().getPosition()).isEqualTo(slot1Position);

        // The fire field is gone: the next spread refills slot 0 instead of moving further out.
        when(gameContext.overlapSphereAll(eq(slot0Position), eq(LEAF_FIELD_RADIUS))).thenReturn(List.of());
        clearInvocations(gameContext);

        grassSpread.update();

        ArgumentCaptor<GameObject> refilled = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(refilled.capture());
        assertThat(refilled.getValue().getPosition()).isEqualTo(slot0Position);
    }

    private static int ringCount(float radius) {
        return Math.max(1, (int) Math.ceil(radius / RING_SPACING));
    }

    private static Vector3 ringSlotPosition(Vector3 center, int ringCount, int ringIndex, int slotIndex) {
        float ringRadius = (ringIndex == ringCount - 1) ? RADIUS : RING_SPACING * (ringIndex + 1);
        float angleOffset = (ringIndex % 2 == 0) ? 0f : (float) Math.PI / QUANTITY_PER_RING;
        double angle = angleOffset + Math.PI * 2 * slotIndex / QUANTITY_PER_RING;
        return center.plus(
                (float) Math.cos(angle) * ringRadius,
                0,
                (float) Math.sin(angle) * ringRadius
        );
    }
}
