package com.wordonline.server.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.service.ParameterService;
import com.wordonline.server.session.service.SessionService;

/**
 * The local profile end to end inside one JVM: the static data fixture is imported, the init token
 * is the only credential, and a session created through {@code POST /api/local/sessions} runs to
 * its end and reports {@code matchStats} and {@code matchResult} instead of writing statistics.
 *
 * <p>{@code local/static.json} is an export of the test environment's static tables in the contract
 * section 1 format, with three bots (the hospitality bot -62, and -17 and -18).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.file.name=build/local-test/local-server.log")
@ActiveProfiles("local")
class LocalSessionIntegrationTest {

    static final String TOKEN = "ab".repeat(32);
    private static final List<Long> DECK = List.of(7L, 7L, 7L, 8L, 8L, 8L, 19L, 19L, 19L, 1L, 1L, 1L, 25L, 25L, 25L);
    private static final ByteArrayOutputStream PROTOCOL_OUTPUT = new ByteArrayOutputStream();

    @TestConfiguration
    static class LocalRuntime {

        @Bean
        LocalInit localInit() throws URISyntaxException {
            Path staticData = Path.of(LocalSessionIntegrationTest.class.getResource("/local/static.json").toURI());
            return new LocalInit(TOKEN, Path.of("build/local-test").toAbsolutePath(), staticData);
        }

        @Bean
        LocalProtocol localProtocol() {
            return new LocalProtocol(PROTOCOL_OUTPUT);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    ParameterService parameterService;

    @Autowired
    SessionService sessionService;

    @Autowired
    LocalDatabase localDatabase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void shortenMatches() {
        // SessionService keeps its registry in a static map, so sessions other test classes left
        // behind would otherwise count as the running local session.
        sessionService.clearSessions();
        jdbcClient.sql("""
                UPDATE parameter_values SET value = 3000
                WHERE game_object_id = (SELECT id FROM game_objects WHERE name = 'game')
                  AND parameter_id = (SELECT id FROM parameters WHERE name = 'duration')
                """).update();
        parameterService.invalidateCache();
    }

    @Test
    void importsTheFixtureAndReportsItsVersion() {
        assertThat(localDatabase.staticVersion()).hasSize(64);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM bot_personas").query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void practiceSessionRunsToTheEndAndPrintsItsResult() throws Exception {
        ResponseEntity<String> response = post(TOKEN, practiceRequest("local-practice-1", 30));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("sessionId").asText()).isEqualTo("local-practice-1");
        assertThat(body.path("webSocketUrl").asText()).isEqualTo("ws://127.0.0.1:" + port + "/ws");

        assertThat(jdbcClient.sql("SELECT novice_progress FROM users WHERE id = 5").query(Double.class).single())
                .isEqualTo(0.5);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM user_magics WHERE user_id = 5 AND count = 3")
                .query(Long.class).single()).isEqualTo(5);

        JsonNode result = awaitLine("matchResult", "local-practice-1");
        assertThat(result.path("outcome").asText()).isIn("WIN", "DRAW");
        assertThat(result.path("tickRate").asInt()).isEqualTo(30);
        assertThat(result.path("frameCount").asInt()).isGreaterThan(30);
        assertThat(result.path("durationMillis").asLong()).isBetween(3000L, 15000L);
        assertThat(result.path("localServerVersion").asText()).isNotBlank();
        assertThat(result.path("decks")).isNotEmpty();
        assertThat(result.path("decks").findValuesAsText("userId")).contains("5", "-17");
        assertThat(result.has("magics")).isTrue();
        if (result.path("outcome").asText().equals("WIN")) {
            assertThat(List.of(result.path("winUserId").asLong(), result.path("lossUserId").asLong()))
                    .containsExactlyInAnyOrder(5L, -17L);
        }

        JsonNode stats = awaitLine("matchStats", "local-practice-1");
        assertThat(stats.path("targetTickRate").asInt()).isEqualTo(30);
        assertThat(stats.path("meanFps").asDouble()).isBetween(20.0, 31.0);
        assertThat(stats.path("p10Fps").asDouble()).isPositive();
        assertThat(indexOf("matchStats", "local-practice-1")).isLessThan(indexOf("matchResult", "local-practice-1"));

        // Statistics stay out of the database: the local schema has no statistic tables at all.
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'statistic%'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void pveSessionRunsItsScenarioToTheEnd() throws Exception {
        // A two second Survive scenario next to the fixture's real ones.
        jdbcClient.sql("MERGE INTO scenarios (id, stage_id) KEY (id) VALUES (9001, 1)").update();
        jdbcClient.sql("""
                MERGE INTO pve_scenario_installers (id, installer_id, prefab_type, master, position_x, position_y,
                    position_z, sort_order, scenario_id, max_hp)
                KEY (id) VALUES (90001, 'nest', 'PveNatureSlimeNest', 'RightPlayer', 14, 0, 5, 1, 9001, 800)
                """).update();
        jdbcClient.sql("MERGE INTO pve_scenario_rules (scenario_id, win_condition, survive_seconds) KEY (scenario_id) "
                + "VALUES (9001, 'Survive', 2)").update();

        Map<String, Object> request = practiceRequest("local-pve-1", 20);
        request.put("type", "PVE");
        request.put("scenarioId", 9001);
        request.put("rightUserId", -1);
        ResponseEntity<String> response = post(TOKEN, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode result = awaitLine("matchResult", "local-pve-1");
        assertThat(result.path("outcome").asText()).isEqualTo("WIN");
        assertThat(result.path("winUserId").asLong()).isEqualTo(5L);
        assertThat(result.path("lossUserId").asLong()).isEqualTo(-1L);
        assertThat(result.path("tickRate").asInt()).isEqualTo(20);
        assertThat(awaitLine("matchStats", "local-pve-1").path("targetTickRate").asInt()).isEqualTo(20);
    }

    @Test
    void newSessionAbandonsTheRunningOne() throws Exception {
        jdbcClient.sql("""
                UPDATE parameter_values SET value = 600000
                WHERE game_object_id = (SELECT id FROM game_objects WHERE name = 'game')
                  AND parameter_id = (SELECT id FROM parameters WHERE name = 'duration')
                """).update();
        parameterService.invalidateCache();

        assertThat(post(TOKEN, practiceRequest("local-first", 20)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // The same request again is a retry, not a new session.
        assertThat(post(TOKEN, practiceRequest("local-first", 20)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sessionService.isSessionActive("local-first")).isTrue();

        assertThat(post(TOKEN, practiceRequest("local-second", 20)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(awaitLine("matchResult", "local-first").path("outcome").asText()).isEqualTo("ABANDONED");
        assertThat(sessionService.isSessionActive("local-second")).isTrue();
        sessionService.getSessionObject("local-second").getGameLoop().close();
    }

    @Test
    void rejectsAnyOtherToken() {
        assertThat(post("cd".repeat(32), practiceRequest("local-rejected", 20)).getStatusCode().is4xxClientError())
                .isTrue();
        assertThat(post(null, practiceRequest("local-rejected", 20)).getStatusCode().is4xxClientError()).isTrue();
        assertThat(sessionService.getSessionObject("local-rejected")).isNull();
    }

    @Test
    void rejectsMalformedRequestsWithBadRequest() {
        Map<String, Object> request = practiceRequest("local-bad", 20);
        request.put("type", "PVP");
        assertThat(post(TOKEN, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> unknownMagic = practiceRequest("local-bad", 20);
        unknownMagic.put("leftDeckCardIds", List.of(999_999L));
        assertThat(post(TOKEN, unknownMagic).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webSocketAcceptsOnlyTheInitToken() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        String url = "ws://127.0.0.1:" + port + "/ws?token={token}";

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + TOKEN);
        StompSession session = client
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() { }, TOKEN)
                .get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();

        String wrong = "cd".repeat(32);
        assertThatThrownBy(() -> client
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() { }, wrong)
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);

        StompHeaders wrongHeaders = new StompHeaders();
        wrongHeaders.add("Authorization", "Bearer " + wrong);
        assertThatThrownBy(() -> client
                .connectAsync(url, new WebSocketHttpHeaders(), wrongHeaders, new StompSessionHandlerAdapter() { }, TOKEN)
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private Map<String, Object> practiceRequest(String sessionId, int tickRate) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("sessionId", sessionId);
        request.put("type", "PRACTICE");
        request.put("scenarioId", null);
        request.put("leftUserId", 5);
        request.put("rightUserId", -17);
        request.put("leftUser", Map.of("id", 5, "name", "player", "email", "player@example.com"));
        request.put("rightUser", Map.of("id", -17, "name", "bot", "email", "bot@theevilent.com"));
        request.put("leftDeckCardIds", DECK);
        request.put("ownedMagicIds", List.of(1, 7, 8, 19, 25));
        request.put("noviceProgress", 0.5);
        request.put("staticVersion", localDatabase.staticVersion());
        request.put("issuedAt", "2026-10-12T03:00:00Z");
        request.put("tickRate", tickRate);
        return request;
    }

    private ResponseEntity<String> post(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("/api/local/sessions", HttpMethod.POST, new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode awaitLine(String type, String sessionId) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> findLine(type, sessionId), Optional::isPresent)
                .orElseThrow();
    }

    private Optional<JsonNode> findLine(String type, String sessionId) throws Exception {
        for (JsonNode line : lines()) {
            if (line.path("type").asText().equals(type) && line.path("sessionId").asText().equals(sessionId)) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    private int indexOf(String type, String sessionId) throws Exception {
        List<JsonNode> lines = lines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).path("type").asText().equals(type)
                    && lines.get(i).path("sessionId").asText().equals(sessionId)) {
                return i;
            }
        }
        return -1;
    }

    private List<JsonNode> lines() throws Exception {
        String output = PROTOCOL_OUTPUT.toString(StandardCharsets.UTF_8);
        // Only complete lines: the last one may still be half written.
        int end = output.lastIndexOf('\n');
        List<JsonNode> lines = new ArrayList<>();
        for (String line : output.substring(0, end + 1).split("\n")) {
            if (!line.isBlank()) {
                lines.add(objectMapper.readTree(line));
            }
        }
        return lines;
    }
}
