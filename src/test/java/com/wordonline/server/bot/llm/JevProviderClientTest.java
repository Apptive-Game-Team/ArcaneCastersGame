package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;

class JevProviderClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final JevProviderClient client = new JevProviderClient(LlmBotProperties.DEFAULT_JEV_URL, "jev-latest");

    @Test
    void writesOnlyModelStateAndTwoChoiceQuestions() throws Exception {
        JsonNode body = JSON.readTree(client.requestBody(LlmTestFixtures.candidates(), "jev-latest"));

        assertThat(fieldNames(body)).containsExactly("model", "state", "questions");
        assertThat(body.path("model").asText()).isEqualTo("jev-latest");
        assertThat(fieldNames(body.path("questions"))).containsExactly("magic", "target");

        JsonNode magic = body.path("questions").path("magic");
        assertThat(fieldNames(magic)).containsExactly("type", "instructions", "criteria");
        assertThat(magic.path("type").asText()).isEqualTo("choice");
        assertThat(fieldNames(magic.path("criteria"))).containsExactly("m3", "wait");

        JsonNode target = body.path("questions").path("target");
        assertThat(target.path("type").asText()).isEqualTo("choice");
        assertThat(fieldNames(target.path("criteria"))).containsExactly("t0", "t1", "t2", "t3", "t4");

        JsonNode state = body.path("state");
        assertThat(fieldNames(state)).containsExactly(
                "side", "board", "mana", "enemyPlayerHp", "hand", "enemyUnits", "ownUnits");
        assertThat(state.path("mana").asInt()).isEqualTo(5);
        assertThat(state.path("hand")).hasSize(2);
        assertThat(state.path("hand").path(1).path("affordable").asBoolean()).isFalse();
    }

    @Test
    void sendsTheBearerKeyAsJson() {
        assertThat(client.headers("sk-test"))
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("Content-Type", "application/json");
    }

    @Test
    void mapsTheChosenKeysBackToTheCast() throws Exception {
        LlmAnswer answer = client.parseResponse("""
                {"model":"jev-latest","answers":{
                  "magic":{"type":"choice","choice":"m3","probabilities":{"m3":0.82,"wait":0.18},"confidence":0.82},
                  "target":{"type":"choice","choice":"t0","probabilities":{"t0":0.7},"confidence":0.7}},
                 "usage":{}}
                """);

        BotBrain.InputDecision decision = answer.toDecision(LlmTestFixtures.candidates(), client.ruleId());

        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target()).isEqualTo(new Vector3(6, 0, 6));
        assertThat(decision.ruleId()).isEqualTo("llm.jev");
        assertThat(decision.reason()).isEqualTo("fire_shot -> enemy FireSlime hp 25 at (6, 6) (confidence 0.82)");
    }

    @Test
    void waitOrUnknownKeysCastNothing() throws Exception {
        LlmCandidates candidates = LlmTestFixtures.candidates();

        assertThat(answer("wait", "t0").toDecision(candidates, "llm.jev")).isNull();
        assertThat(answer("m7", "t0").toDecision(candidates, "llm.jev")).isNull();
        assertThat(answer("m3", "t99").toDecision(candidates, "llm.jev")).isNull();
    }

    @Test
    void anAnswerWithoutAMagicChoiceIsAFailure() {
        assertThatThrownBy(() -> client.parseResponse("{\"answers\":{}}")).isInstanceOf(LlmCallFailure.class);
        assertThatThrownBy(() -> client.parseResponse("<html>")).isInstanceOf(LlmCallFailure.class);
    }

    private LlmAnswer answer(String magic, String target) throws LlmCallFailure {
        return client.parseResponse("{\"answers\":{\"magic\":{\"choice\":\"" + magic
                + "\"},\"target\":{\"choice\":\"" + target + "\"}}}");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
