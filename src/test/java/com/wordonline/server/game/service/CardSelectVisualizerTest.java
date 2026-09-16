package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardSelectVisualizerTest {

    private static final long USER_ID = 7L;
    private static final long ELEMENTLESS_MAGIC_ID = 1L;
    private static final long FIRE_MAGIC_ID = 2L;
    private static final long UNKNOWN_MAGIC_ID = 99L;

    private final DatabaseMagicParser magicParser = mock(DatabaseMagicParser.class);
    private final CardSelectVisualizer visualizer = new CardSelectVisualizer(magicParser);
    private GameContext gameContext;
    private GameObject player;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        player = new GameObject(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, gameContext);
        when(gameContext.findPlayerGameObject(USER_ID)).thenReturn(Optional.of(player));
        when(magicParser.getMagic(ELEMENTLESS_MAGIC_ID)).thenReturn(magicWith(ElementType.NONE));
        when(magicParser.getMagic(FIRE_MAGIC_ID)).thenReturn(magicWith(ElementType.FIRE));
        when(magicParser.getMagic(UNKNOWN_MAGIC_ID)).thenReturn(null);
    }

    @Test
    void everyElementMarksTheSelection() {
        long magicId = 10L;
        for (ElementType element : ElementType.values()) {
            long id = magicId++;
            when(magicParser.getMagic(id)).thenReturn(magicWith(element));

            visualizer.selectCard(gameContext, USER_ID, id);
            assertThat(player.getEffects()).contains(Effect.CardSelected);

            visualizer.unselectCard(gameContext, USER_ID, id);
            player.flushComponents();
            assertThat(player.getEffects()).doesNotContain(Effect.CardSelected);
        }
    }

    @Test
    void anElementlessMagicStillMarksTheSelection() {
        visualizer.selectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);

        assertThat(player.getEffects()).containsExactly(Effect.CardSelected);
    }

    @Test
    void anElementMagicKeepsItsIdleAura() {
        visualizer.selectCard(gameContext, USER_ID, FIRE_MAGIC_ID);
        assertThat(player.getEffects()).contains(Effect.FireIdleAura, Effect.CardSelected);

        visualizer.unselectCard(gameContext, USER_ID, FIRE_MAGIC_ID);
        assertThat(player.getEffects()).doesNotContain(Effect.FireIdleAura, Effect.CardSelected);
    }

    @Test
    void theMarkerStaysUntilTheLastSelectedCardGoes() {
        visualizer.selectCard(gameContext, USER_ID, FIRE_MAGIC_ID);
        visualizer.selectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);

        visualizer.unselectCard(gameContext, USER_ID, FIRE_MAGIC_ID);
        assertThat(player.getEffects()).contains(Effect.CardSelected);

        visualizer.unselectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);
        assertThat(player.getEffects()).doesNotContain(Effect.CardSelected);
    }

    @Test
    void aSuccessfulCastClearsTheMarker() {
        visualizer.selectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);
        player.flushComponents();

        player.setStatus(Status.Attack);

        assertThat(player.getEffects()).doesNotContain(Effect.CardSelected);
    }

    @Test
    void anUnknownMagicIdStillMarksTheSelectionAndTheRejectedCastClearsIt() {
        visualizer.selectCard(gameContext, USER_ID, UNKNOWN_MAGIC_ID);
        assertThat(player.getEffects()).containsExactly(Effect.CardSelected);
        player.flushComponents();

        player.setStatus(Status.Hindered);

        assertThat(player.getEffects()).doesNotContain(Effect.CardSelected);
    }

    @Test
    void aCardSelectedInTheSameFrameAsACastBuildsAFreshMarker() {
        visualizer.selectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);
        player.flushComponents();
        player.setStatus(Status.Attack);

        // the expired effect is still in the component list until the loop flushes removals
        visualizer.selectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);
        assertThat(player.getEffects()).contains(Effect.CardSelected);

        visualizer.unselectCard(gameContext, USER_ID, ELEMENTLESS_MAGIC_ID);
        assertThat(player.getEffects()).doesNotContain(Effect.CardSelected);
    }

    private static Magic magicWith(ElementType element) {
        Magic magic = new Magic() {
            @Override
            public void run(GameContext gameContext, Master master, Vector3 position) {
            }
        };
        magic.element = element;
        return magic;
    }
}
