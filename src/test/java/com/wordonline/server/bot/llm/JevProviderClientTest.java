package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

class JevProviderClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final JevProviderClient client = new JevProviderClient(LlmBotProperties.DEFAULT_JEV_URL, "jev-latest");

    @Test
    void asksOneChoiceQuestionWhoseOptionsAreCompletePlays() throws Exception {
        JsonNode body = JSON.readTree(client.requestBody(LlmTestFixtures.candidates(), "jev-latest"));

        assertThat(fieldNames(body)).containsExactly("model", "state", "questions");
        assertThat(body.path("model").asText()).isEqualTo("jev-latest");
        assertThat(fieldNames(body.path("questions"))).containsExactly("play");

        JsonNode play = body.path("questions").path("play");
        assertThat(fieldNames(play)).containsExactly("type", "instructions", "criteria");
        assertThat(play.path("type").asText()).isEqualTo("choice");
        assertThat(play.path("instructions").asText())
                .contains("win the lane battle", "Defend first", "never wait while enemy units are close");
        assertThat(fieldNames(play.path("criteria"))).containsExactly("p0", "p1");
        assertThat(play.path("criteria").path("p0").asText()).startsWith("fire_shot (damage spell, cost 3");

        JsonNode state = body.path("state");
        assertThat(state.path("ourHp").asInt()).isEqualTo(90);
        assertThat(state.path("enemyHp").asInt()).isEqualTo(88);
        assertThat(state.path("mana").asInt()).isEqualTo(5);
    }

    @Test
    void aRealisticBodyStaysWithinTheApiLimits() throws Exception {
        BotBrain.InputDecision rule = new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID, new Vector3(5, 0, 5),
                "combo.mob-cluster", "AbstractExplosionMagic targets a cluster of 3 enemy mobs.");
        LlmCandidates candidates = LlmTestFixtures.builder().build(
                LlmTestFixtures.eye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.ROCK_GOLEM_ID,
                        LlmTestFixtures.SLIME_SUMMON_ID), 9),
                LlmTestFixtures.parameters(), Master.LeftPlayer, rule);

        String body = client.requestBody(candidates, "jev-latest");

        assertThat(body.getBytes(StandardCharsets.UTF_8).length).isLessThan(256_000);
        JsonNode criteria = JSON.readTree(body).path("questions").path("play").path("criteria");
        assertThat(criteria.size()).isBetween(2, 255);
        assertThat(criteria.path("p0").asText()).startsWith("STANDARD TACTIC");
    }

    @Test
    void sendsTheBearerKeyAsJson() {
        assertThat(client.headers("sk-test"))
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("Content-Type", "application/json");
    }

    @Test
    void mapsTheChosenPlayBackToTheCast() throws Exception {
        LlmAnswer answer = client.parseResponse("""
                {"model":"jev-latest","answers":{
                  "play":{"type":"choice","choice":"p1","probabilities":{"p0":0.18,"p1":0.82},"confidence":0.82}},
                 "usage":{}}
                """);

        BotBrain.InputDecision decision = answer.toDecision(LlmTestFixtures.candidates(), client.ruleId());

        assertThat(answer.playKey()).isEqualTo("p1");
        assertThat(answer.confidence()).isEqualTo(0.82);
        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target()).isEqualTo(new Vector3(3.5f, 0, 4));
        assertThat(decision.ruleId()).isEqualTo("llm.jev");
        assertThat(decision.reason())
                .startsWith("(0.82) fire_shot (damage spell, cost 3, 20 damage in radius 1.5) at (3.5, 4)")
                .hasSizeLessThanOrEqualTo(LlmAnswer.MAX_REASON_LENGTH);
    }

    @Test
    void waitOrUnknownKeysCastNothing() throws Exception {
        LlmCandidates candidates = LlmTestFixtures.candidates();

        assertThat(answer("wait").toDecision(candidates, "llm.jev")).isNull();
        assertThat(answer("p99").toDecision(candidates, "llm.jev")).isNull();
        assertThat(answer("m3").toDecision(candidates, "llm.jev")).isNull();
    }

    @Test
    void anAnswerWithoutAPlayChoiceIsAFailure() {
        assertThatThrownBy(() -> client.parseResponse("{\"answers\":{}}")).isInstanceOf(LlmCallFailure.class);
        assertThatThrownBy(() -> client.parseResponse("{\"answers\":{\"magic\":{\"choice\":\"m3\"}}}"))
                .isInstanceOf(LlmCallFailure.class);
        assertThatThrownBy(() -> client.parseResponse("<html>")).isInstanceOf(LlmCallFailure.class);
    }

    private LlmAnswer answer(String play) throws LlmCallFailure {
        return client.parseResponse("{\"answers\":{\"play\":{\"choice\":\"" + play + "\"}}}");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
