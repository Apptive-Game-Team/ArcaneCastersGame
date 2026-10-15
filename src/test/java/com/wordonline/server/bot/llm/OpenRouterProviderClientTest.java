package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;

class OpenRouterProviderClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenRouterProviderClient client =
            new OpenRouterProviderClient(LlmBotProperties.DEFAULT_OPENROUTER_URL, "openrouter/auto");

    @Test
    void writesTheDocumentedChatCompletionBody() throws Exception {
        JsonNode body = JSON.readTree(client.requestBody(LlmTestFixtures.candidates(), "openrouter/auto"));

        assertThat(body.path("model").asText()).isEqualTo("openrouter/auto");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(200);
        assertThat(body.path("messages")).hasSize(2);
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").asText()).contains("\"magic\"", "\"target\"", "\"reason\"");
        String user = body.path("messages").path(1).path("content").asText();
        assertThat(body.path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(user).contains("\"mana\":5", "\"m3\":\"fire_shot, 3 mana", "\"wait\"", "\"t2\":\"enemy base");
    }

    @Test
    void sendsTheTitleHeader() {
        assertThat(client.headers("sk-or"))
                .containsEntry("Authorization", "Bearer sk-or")
                .containsEntry("X-Title", "ArcaneCasters");
    }

    @Test
    void readsTheJsonAnswerFromTheMessageContent() throws Exception {
        LlmAnswer answer = client.parseResponse(response(
                "{\\\"magic\\\":\\\"m3\\\",\\\"target\\\":\\\"t1\\\",\\\"reason\\\":\\\"Burn the slime before it reaches my base\\\"}"));

        BotBrain.InputDecision decision = answer.toDecision(LlmTestFixtures.candidates(), client.ruleId());

        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target()).isEqualTo(new Vector3(12, 0, 4));
        assertThat(decision.ruleId()).isEqualTo("llm.openrouter");
        assertThat(decision.reason()).isEqualTo("Burn the slime before it reaches my base");
    }

    @Test
    void toleratesCodeFences() throws Exception {
        LlmAnswer answer = client.parseResponse(response(
                "```json\\n{\\\"magic\\\": \\\"wait\\\", \\\"target\\\": \\\"t0\\\", \\\"reason\\\": \\\"saving\\\"}\\n```"));

        assertThat(answer.magicKey()).isEqualTo("wait");
        assertThat(answer.toDecision(LlmTestFixtures.candidates(), client.ruleId())).isNull();
    }

    @Test
    void truncatesALongReason() throws Exception {
        String reason = "x".repeat(300);
        LlmAnswer answer = client.parseResponse(response(
                "{\\\"magic\\\":\\\"m3\\\",\\\"target\\\":\\\"t0\\\",\\\"reason\\\":\\\"" + reason + "\\\"}"));

        assertThat(answer.toDecision(LlmTestFixtures.candidates(), client.ruleId()).reason())
                .hasSize(LlmAnswer.MAX_REASON_LENGTH);
    }

    @Test
    void unknownKeysCastNothing() throws Exception {
        LlmAnswer answer = client.parseResponse(response("{\\\"magic\\\":\\\"m999\\\",\\\"target\\\":\\\"t0\\\"}"));

        assertThat(answer.toDecision(LlmTestFixtures.candidates(), client.ruleId())).isNull();
    }

    @Test
    void contentWithoutJsonIsAFailure() {
        assertThatThrownBy(() -> client.parseResponse(response("I would cast fire shot.")))
                .isInstanceOf(LlmCallFailure.class);
        assertThatThrownBy(() -> client.parseResponse("{\"choices\":[]}"))
                .isInstanceOf(LlmCallFailure.class);
    }

    private static String response(String escapedContent) {
        return "{\"id\":\"gen-1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + escapedContent + "\"}}]}";
    }
}
