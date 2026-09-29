package com.wordonline.server.session.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wordonline.server.game.domain.SessionType;

/**
 * Pins the wire format of {@code POST /api/server/game-sessions} against fixtures shared
 * with WordOnlineMatching, which keeps byte-identical copies under the same paths.
 *
 * <p>Both repositories previously checked this contract only against their own DTO
 * classes, so a renamed field left both suites green. It would not have surfaced as an
 * exception either: the lobby treats a response it cannot match to its {@code attemptId}
 * as a refusal and moves to the next candidate, so a broken contract reads as "every
 * server refused" and ends in a 503 with nothing in the logs naming the cause.
 */
class LobbyContractTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contract/" + name)) {
            assertThat(in).as("missing fixture: %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 로비가_보내는_요청을_그대로_읽는다() throws IOException {
        CreateSessionRequest request =
                objectMapper.readValue(fixture("create-session-request.json"), CreateSessionRequest.class);

        assertThat(request.attemptId()).isEqualTo("attempt-1");
        assertThat(request.sessionId()).isEqualTo("session-1");
        assertThat(request.uid1()).isEqualTo(1L);
        assertThat(request.uid2()).isEqualTo(2L);
        assertThat(request.sessionType()).isEqualTo(SessionType.PVP);
        assertThat(request.scenarioId()).isNull();
    }

    @Test
    void 읽어들인_요청이_세션_생성에_쓰이는_값을_잃지_않는다() throws IOException {
        CreateSessionRequest request =
                objectMapper.readValue(fixture("create-session-request.json"), CreateSessionRequest.class);

        SessionDto sessionDto = request.toSessionDto();

        assertThat(sessionDto.sessionId()).isEqualTo("session-1");
        assertThat(sessionDto.uid1()).isEqualTo(1L);
        assertThat(sessionDto.uid2()).isEqualTo(2L);
        assertThat(sessionDto.sessionType()).isEqualTo(SessionType.PVP);
        assertThat(sessionDto.scenarioId()).isNull();
    }

    @Test
    void 이_서버가_보내는_응답이_공유_픽스처와_일치한다() throws IOException {
        SessionReadyResponse response = new SessionReadyResponse(
                "attempt-1", "session-1", true, "http://localhost:7777", "http://localhost:7777/ws",
                "00000000-0000-0000-0000-000000000001");

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(response)))
                .isEqualTo(objectMapper.readTree(fixture("session-ready-response.json")));
    }

    @Test
    void 응답은_세션을_만든_프로세스의_인스턴스_식별자를_싣는다() throws IOException {
        // The lobby pins this onto the ticket. Without it a restarted server, back on the same
        // domain and port and passing health checks, leaves the ticket stuck in MATCHED forever.
        JsonNode json = objectMapper.readTree(fixture("session-ready-response.json"));

        assertThat(json.has("instanceId")).isTrue();
        assertThat(json.get("instanceId").isTextual()).isTrue();
    }

    @Test
    void 로비가_모르는_필드를_보내도_요청_파싱이_깨지지_않는다() throws IOException {
        // The lobby may ship a field before this server knows about it. Rejecting the whole
        // request there reads as a refusal on the lobby side and fails matching for no reason.
        ObjectNode extended = (ObjectNode) objectMapper.readTree(fixture("create-session-request.json"));
        extended.put("queuedAt", 1786000000000L);

        CreateSessionRequest request =
                objectMapper.readValue(extended.toString(), CreateSessionRequest.class);

        assertThat(request.sessionId()).isEqualTo("session-1");
        assertThat(request.attemptId()).isEqualTo("attempt-1");
    }

    @Test
    void 요청은_세션을_감싸지_않고_평평하게_온다() throws IOException {
        // A wrapper object would leave uid1/uid2/sessionType null here, and the lobby would
        // read the resulting refusal as "no server available".
        JsonNode json = objectMapper.readTree(fixture("create-session-request.json"));

        assertThat(json.has("session")).isFalse();
        assertThat(json.has("sessionDto")).isFalse();
        assertThat(json.properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "attemptId", "sessionId", "uid1", "uid2", "sessionType", "scenarioId",
                        "leftDeckCardIds", "rightDeckCardIds");
    }

    @Test
    void 매칭_덱_스냅샷은_마법_아이디_배열로_온다() throws IOException {
        // The fixture carries the two deck fields as null because a selected-deck match sends
        // no snapshot. A random-deck match fills them, so pin the element type here: a string
        // array on the lobby side would bind to null and silently fall back to the selected deck.
        ObjectNode withDecks = (ObjectNode) objectMapper.readTree(fixture("create-session-request.json"));
        withDecks.set("leftDeckCardIds", objectMapper.valueToTree(java.util.List.of(1L, 1L, 7L)));
        withDecks.set("rightDeckCardIds", objectMapper.valueToTree(java.util.List.of(9L, 12L)));

        CreateSessionRequest request =
                objectMapper.readValue(withDecks.toString(), CreateSessionRequest.class);

        assertThat(request.leftDeckCardIds()).containsExactly(1L, 1L, 7L);
        assertThat(request.rightDeckCardIds()).containsExactly(9L, 12L);
        assertThat(request.toSessionDto().leftDeckCardIds()).containsExactly(1L, 1L, 7L);
    }

    @Test
    void 스냅샷_없는_요청은_덱_필드가_비어서_온다() throws IOException {
        CreateSessionRequest request =
                objectMapper.readValue(fixture("create-session-request.json"), CreateSessionRequest.class);

        assertThat(request.leftDeckCardIds()).isNull();
        assertThat(request.rightDeckCardIds()).isNull();
    }
}
