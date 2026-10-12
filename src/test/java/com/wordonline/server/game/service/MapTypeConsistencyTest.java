package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.deck.service.DeckService;
import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.config.GameMapProperties.Selection;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.game.domain.map.GameMapSelector;
import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.repository.MapFixtureDatabase;
import com.wordonline.server.game.repository.PveScenarioRepository;
import com.wordonline.server.lobby.client.LobbySessionClient;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerInstanceIdProvider;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import com.wordonline.server.session.controller.SessionServerController;
import com.wordonline.server.session.dto.CreateSessionRequest;
import com.wordonline.server.session.dto.SessionReadyResponse;
import com.wordonline.server.session.service.SessionService;
import com.wordonline.server.session.util.GameLoopFactory;
import com.wordonline.server.session.util.SessionObjectFactory;
import com.wordonline.server.statistic.service.GameSessionRecordService;
import com.wordonline.server.statistic.service.StatisticService;

/**
 * The value in the creation response and the value the loop spawns terrain from must be the same
 * one. Everything between the lobby request and the loop is real here (controller, session
 * service, session object factory, map selector, loop initialization, and the PVE lookup against
 * the H2 fixtures); only the thread, the statistics and the deck lookups are replaced.
 */
class MapTypeConsistencyTest {

    private final RandomGenerator random = mock(RandomGenerator.class);
    private final GameContext gameContext = mock(GameContext.class);
    private SessionService sessionService;
    private SessionServerController controller;

    @BeforeEach
    void setUp() {
        PveScenarioRepository repository = new PveScenarioRepository(MapFixtureDatabase.create());
        GameMapSelector selector = new GameMapSelector(new GameMapProperties(Selection.RANDOM), random, repository);
        DeckService deckService = mock(DeckService.class);
        when(deckService.getParticipantCards(any(Long.class), any())).thenReturn(List.of());
        when(deckService.getParticipantCards(any(Long.class))).thenReturn(List.of());
        when(deckService.getSelectedCards(any(Long.class))).thenReturn(List.of());
        SessionObjectFactory sessionObjectFactory =
                new SessionObjectFactory(mock(SimpMessagingTemplate.class), deckService, selector);

        when(gameContext.getParameters()).thenReturn(mock(Parameters.class));
        GameLoopFactory gameLoopFactory = mock(GameLoopFactory.class);
        when(gameLoopFactory.create(any())).thenAnswer(invocation -> new GameLoop(
                mock(MmrService.class), mock(UserService.class), gameContext, mock(Parameters.class)) {
            private final AtomicBoolean started = new AtomicBoolean(true);

            @Override
            void update() {
            }

            // The thread must not tick the mocks; the session is ready as soon as it is created.
            @Override
            public void run() {
            }

            @Override
            public boolean awaitStart(Duration timeout) {
                return started.get();
            }
        });

        sessionService = new SessionService(sessionObjectFactory, gameLoopFactory, mock(StatisticService.class),
                mock(GameSessionRecordService.class), mock(UserService.class), mock(BotPersonaService.class),
                mock(UserScenarioService.class), mock(LobbySessionClient.class));
        sessionService.clearSessions();

        ServerStatusService serverStatusService = mock(ServerStatusService.class);
        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
        ServerUrlProvider serverUrlProvider = mock(ServerUrlProvider.class);
        when(serverUrlProvider.getServerUrl()).thenReturn("https://game.example.com");
        controller = new SessionServerController(
                sessionService, serverStatusService, serverUrlProvider, new ServerInstanceIdProvider());
    }

    @AfterEach
    void tearDown() {
        sessionService.clearSessions();
    }

    @Test
    void pvpResponseAndLoopAgreeOnGrasslandAndOnRiver() {
        for (boolean coin : new boolean[] {false, true}) {
            when(random.nextBoolean()).thenReturn(coin);
            GameMap expected = coin ? GameMap.RIVER : GameMap.GRASSLAND;

            assertResponseAndLoopAgree("pvp-" + coin, SessionType.PVP, 1L, 2L, null, expected);
        }
    }

    @Test
    void practiceAndBotResponsesAndLoopAgree() {
        for (boolean coin : new boolean[] {false, true}) {
            when(random.nextBoolean()).thenReturn(coin);
            GameMap expected = coin ? GameMap.RIVER : GameMap.GRASSLAND;

            assertResponseAndLoopAgree("practice-" + coin, SessionType.Practice, 1L, -12L, null, expected);
            assertResponseAndLoopAgree("bot-" + coin, SessionType.Practice, -3L, -12L, null, expected);
        }
    }

    @Test
    void pveResponseCarriesTheStageMapAndTheLoopSpawnsNoTerrain() {
        assertResponseAndLoopAgree("pve-forest", SessionType.PVE, 1L, -1L, 1L, GameMap.FOREST);
        assertResponseAndLoopAgree("pve-fortress", SessionType.PVE, 1L, -1L, 2L, GameMap.FORTRESS);
        assertResponseAndLoopAgree("pve-gate", SessionType.PVE, 1L, -1L, 3L, GameMap.GATE);
        assertResponseAndLoopAgree("pve-default", SessionType.PVE, 1L, -1L, 4L, GameMap.GRASSLAND);
        assertResponseAndLoopAgree("pve-orphan", SessionType.PVE, 1L, -1L, 5L, GameMap.GRASSLAND);
        assertResponseAndLoopAgree("pve-no-stage", SessionType.PVE, 1L, -1L, 6L, GameMap.GRASSLAND);
        // Two stages of one adventure: the map follows the stage.
        assertResponseAndLoopAgree("pve-twin-forest", SessionType.PVE, 1L, -1L, 7L, GameMap.FOREST);
        assertResponseAndLoopAgree("pve-twin-river", SessionType.PVE, 1L, -1L, 8L, GameMap.RIVER);
    }

    private void assertResponseAndLoopAgree(String sessionId, SessionType type, long uid1, long uid2,
                                            Long scenarioId, GameMap expected) {
        org.mockito.Mockito.reset(gameContext);
        when(gameContext.getParameters()).thenReturn(mock(Parameters.class));

        ResponseEntity<SessionReadyResponse> response =
                controller.createGameSession(request(sessionId, type, uid1, uid2, scenarioId));

        GameMap inResponse = response.getBody().mapType();
        assertThat(inResponse).as(sessionId).isEqualTo(expected);
        assertThat(inResponse).isNotNull();

        // What the loop did with it: the terrain it set and the cells it spawned.
        GameMap inLoop = sessionService.getSessionObject(sessionId).getMap();
        assertThat(inLoop).isSameAs(inResponse);
        verify(gameContext).setTerrain(inResponse.terrain());

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, org.mockito.Mockito.atLeastOnce()).createGameObject(spawned.capture());
        long water = spawned.getAllValues().stream().filter(o -> o.getType() == PrefabType.RiverWater).count();
        long bridges = spawned.getAllValues().stream().filter(o -> o.getType() == PrefabType.RiverBridge).count();
        if (inResponse == GameMap.RIVER) {
            assertThat(water).isEqualTo(Terrain.RIVER.waterCells().size());
            assertThat(bridges).isEqualTo(Terrain.RIVER.bridgeCells().size());
        } else {
            assertThat(water).as(sessionId).isZero();
            assertThat(bridges).as(sessionId).isZero();
        }
    }

    private CreateSessionRequest request(String sessionId, SessionType type, long uid1, long uid2, Long scenarioId) {
        return new CreateSessionRequest("attempt-" + sessionId, sessionId, uid1, uid2, type, scenarioId);
    }
}
